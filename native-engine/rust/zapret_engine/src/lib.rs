use jni::JNIEnv as SafeJniEnv;
use jni::JavaVM;
use jni::objects::{GlobalRef, JClass as SafeJClass, JObject, JValue};
#[cfg(target_os = "android")]
use socket2::{Domain, Protocol, Socket, Type};
use std::ffi::c_void;
use std::io::{self, Read, Write};
use std::net::{Shutdown, SocketAddr, TcpListener, TcpStream, UdpSocket};
#[cfg(target_os = "android")]
use std::net::ToSocketAddrs;
#[cfg(target_os = "android")]
use std::os::fd::AsRawFd;
use std::os::raw::c_char;
use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU64, AtomicU8, AtomicUsize, Ordering};
use std::sync::{Mutex, OnceLock};
use std::thread::{self, JoinHandle};
use std::time::Duration;

const ENGINE_VERSION: &str = "zapret-engine-rust/0.1.0";
const MAX_INITIAL_BYTES: usize = 16 * 1024;
const PROFILE_COMPATIBLE: u8 = 0;
const PROFILE_BALANCED: u8 = 1;
const PROFILE_AGGRESSIVE: u8 = 2;
const PROFILE_ZAPTRET2: u8 = 3;
const PROFILE_CUSTOM: u8 = 4;
const CUSTOM_DELAY_MAX_MS: u64 = 5_000;
static RUNNING: AtomicBool = AtomicBool::new(false);
static BLOCK_UDP_443: AtomicBool = AtomicBool::new(true);
static STRATEGY_PROFILE: AtomicU8 = AtomicU8::new(PROFILE_BALANCED);
static CUSTOM_SPLIT: AtomicUsize = AtomicUsize::new(1);
static CUSTOM_DELAY_MS: AtomicU64 = AtomicU64::new(0);
static CONNECTION_FAILURES: AtomicU32 = AtomicU32::new(0);
static SERVER: OnceLock<Mutex<Option<JoinHandle<()>>>> = OnceLock::new();
static SOCKET_PROTECTOR: OnceLock<Mutex<Option<SocketProtector>>> = OnceLock::new();

struct SocketProtector {
    vm: JavaVM,
    service: GlobalRef,
}

#[repr(C)]
pub struct JNIEnv {
    functions: *const c_void,
}

#[repr(C)]
pub struct JClass(*mut c_void);

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_zapret_mobile_NativeZapretEngine_version(
    env: *mut JNIEnv,
    _class: JClass,
) -> *mut c_void {
    jni_new_string(env, ENGINE_VERSION)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_zapret_mobile_NativeZapretEngine_start(
    _env: *mut JNIEnv,
    _class: JClass,
    socks_port: i32,
) -> i32 {
    if socks_port <= 0 || socks_port > u16::MAX as i32 {
        return -1;
    }
    if RUNNING.swap(true, Ordering::SeqCst) {
        return 0;
    }

    let port = socks_port as u16;
    let handle = thread::spawn(move || run_socks_server(port));
    let cell = SERVER.get_or_init(|| Mutex::new(None));
    if let Ok(mut slot) = cell.lock() {
        *slot = Some(handle);
        0
    } else {
        RUNNING.store(false, Ordering::SeqCst);
        -2
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_zapret_mobile_NativeZapretEngine_configure(
    env: SafeJniEnv<'_>,
    _class: SafeJClass<'_>,
    vpn_service: JObject<'_>,
    profile_id: i32,
    block_quic: u8,
) -> i32 {
    if !(PROFILE_COMPATIBLE as i32..=PROFILE_CUSTOM as i32).contains(&profile_id) {
        return -1;
    }
    let vm = match env.get_java_vm() {
        Ok(vm) => vm,
        Err(_) => return -2,
    };
    let service = match env.new_global_ref(vpn_service) {
        Ok(service) => service,
        Err(_) => return -3,
    };
    let protector = SOCKET_PROTECTOR.get_or_init(|| Mutex::new(None));
    let Ok(mut slot) = protector.lock() else {
        return -4;
    };
    *slot = Some(SocketProtector { vm, service });

    STRATEGY_PROFILE.store(profile_id as u8, Ordering::SeqCst);
    BLOCK_UDP_443.store(block_quic != 0, Ordering::SeqCst);
    CONNECTION_FAILURES.store(0, Ordering::SeqCst);
    0
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_zapret_mobile_NativeZapretEngine_configureCustomStrategy(
    _env: *mut JNIEnv,
    _class: JClass,
    split_position: i32,
    delay_ms: i64,
) -> i32 {
    if split_position < 1 || split_position as usize > MAX_INITIAL_BYTES {
        return -1;
    }
    if delay_ms < 0 || delay_ms as u64 > CUSTOM_DELAY_MAX_MS {
        return -2;
    }
    CUSTOM_SPLIT.store(split_position as usize, Ordering::SeqCst);
    CUSTOM_DELAY_MS.store(delay_ms as u64, Ordering::SeqCst);
    0
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_zapret_mobile_NativeZapretEngine_pollFailureCount(
    _env: *mut JNIEnv,
    _class: JClass,
) -> i32 {
    CONNECTION_FAILURES.swap(0, Ordering::Relaxed) as i32
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_zapret_mobile_NativeZapretEngine_stop(
    _env: *mut JNIEnv,
    _class: JClass,
) {
    RUNNING.store(false, Ordering::SeqCst);
    let _ = TcpStream::connect("127.0.0.1:1080");
    if let Some(cell) = SERVER.get() {
        if let Ok(mut slot) = cell.lock() {
            if let Some(handle) = slot.take() {
                let _ = handle.join();
            }
        }
    }
}

type NewStringUtf = unsafe extern "system" fn(*mut JNIEnv, *const c_char) -> *mut c_void;

#[repr(C)]
struct JniNativeInterface {
    reserved0: *const c_void,
    reserved1: *const c_void,
    reserved2: *const c_void,
    reserved3: *const c_void,
    get_version: *const c_void,
    define_class: *const c_void,
    find_class: *const c_void,
    from_reflected_method: *const c_void,
    from_reflected_field: *const c_void,
    to_reflected_method: *const c_void,
    get_superclass: *const c_void,
    is_assignable_from: *const c_void,
    to_reflected_field: *const c_void,
    throw: *const c_void,
    throw_new: *const c_void,
    exception_occurred: *const c_void,
    exception_describe: *const c_void,
    exception_clear: *const c_void,
    fatal_error: *const c_void,
    push_local_frame: *const c_void,
    pop_local_frame: *const c_void,
    new_global_ref: *const c_void,
    delete_global_ref: *const c_void,
    delete_local_ref: *const c_void,
    is_same_object: *const c_void,
    new_local_ref: *const c_void,
    ensure_local_capacity: *const c_void,
    alloc_object: *const c_void,
    new_object: *const c_void,
    new_object_v: *const c_void,
    new_object_a: *const c_void,
    get_object_class: *const c_void,
    is_instance_of: *const c_void,
    get_method_id: *const c_void,
    call_object_method: *const c_void,
    call_object_method_v: *const c_void,
    call_object_method_a: *const c_void,
    call_boolean_method: *const c_void,
    call_boolean_method_v: *const c_void,
    call_boolean_method_a: *const c_void,
    call_byte_method: *const c_void,
    call_byte_method_v: *const c_void,
    call_byte_method_a: *const c_void,
    call_char_method: *const c_void,
    call_char_method_v: *const c_void,
    call_char_method_a: *const c_void,
    call_short_method: *const c_void,
    call_short_method_v: *const c_void,
    call_short_method_a: *const c_void,
    call_int_method: *const c_void,
    call_int_method_v: *const c_void,
    call_int_method_a: *const c_void,
    call_long_method: *const c_void,
    call_long_method_v: *const c_void,
    call_long_method_a: *const c_void,
    call_float_method: *const c_void,
    call_float_method_v: *const c_void,
    call_float_method_a: *const c_void,
    call_double_method: *const c_void,
    call_double_method_v: *const c_void,
    call_double_method_a: *const c_void,
    call_void_method: *const c_void,
    call_void_method_v: *const c_void,
    call_void_method_a: *const c_void,
    call_nonvirtual_object_method: *const c_void,
    call_nonvirtual_object_method_v: *const c_void,
    call_nonvirtual_object_method_a: *const c_void,
    call_nonvirtual_boolean_method: *const c_void,
    call_nonvirtual_boolean_method_v: *const c_void,
    call_nonvirtual_boolean_method_a: *const c_void,
    call_nonvirtual_byte_method: *const c_void,
    call_nonvirtual_byte_method_v: *const c_void,
    call_nonvirtual_byte_method_a: *const c_void,
    call_nonvirtual_char_method: *const c_void,
    call_nonvirtual_char_method_v: *const c_void,
    call_nonvirtual_char_method_a: *const c_void,
    call_nonvirtual_short_method: *const c_void,
    call_nonvirtual_short_method_v: *const c_void,
    call_nonvirtual_short_method_a: *const c_void,
    call_nonvirtual_int_method: *const c_void,
    call_nonvirtual_int_method_v: *const c_void,
    call_nonvirtual_int_method_a: *const c_void,
    call_nonvirtual_long_method: *const c_void,
    call_nonvirtual_long_method_v: *const c_void,
    call_nonvirtual_long_method_a: *const c_void,
    call_nonvirtual_float_method: *const c_void,
    call_nonvirtual_float_method_v: *const c_void,
    call_nonvirtual_float_method_a: *const c_void,
    call_nonvirtual_double_method: *const c_void,
    call_nonvirtual_double_method_v: *const c_void,
    call_nonvirtual_double_method_a: *const c_void,
    call_nonvirtual_void_method: *const c_void,
    call_nonvirtual_void_method_v: *const c_void,
    call_nonvirtual_void_method_a: *const c_void,
    get_field_id: *const c_void,
    get_object_field: *const c_void,
    get_boolean_field: *const c_void,
    get_byte_field: *const c_void,
    get_char_field: *const c_void,
    get_short_field: *const c_void,
    get_int_field: *const c_void,
    get_long_field: *const c_void,
    get_float_field: *const c_void,
    get_double_field: *const c_void,
    set_object_field: *const c_void,
    set_boolean_field: *const c_void,
    set_byte_field: *const c_void,
    set_char_field: *const c_void,
    set_short_field: *const c_void,
    set_int_field: *const c_void,
    set_long_field: *const c_void,
    set_float_field: *const c_void,
    set_double_field: *const c_void,
    get_static_method_id: *const c_void,
    call_static_object_method: *const c_void,
    call_static_object_method_v: *const c_void,
    call_static_object_method_a: *const c_void,
    call_static_boolean_method: *const c_void,
    call_static_boolean_method_v: *const c_void,
    call_static_boolean_method_a: *const c_void,
    call_static_byte_method: *const c_void,
    call_static_byte_method_v: *const c_void,
    call_static_byte_method_a: *const c_void,
    call_static_char_method: *const c_void,
    call_static_char_method_v: *const c_void,
    call_static_char_method_a: *const c_void,
    call_static_short_method: *const c_void,
    call_static_short_method_v: *const c_void,
    call_static_short_method_a: *const c_void,
    call_static_int_method: *const c_void,
    call_static_int_method_v: *const c_void,
    call_static_int_method_a: *const c_void,
    call_static_long_method: *const c_void,
    call_static_long_method_v: *const c_void,
    call_static_long_method_a: *const c_void,
    call_static_float_method: *const c_void,
    call_static_float_method_v: *const c_void,
    call_static_float_method_a: *const c_void,
    call_static_double_method: *const c_void,
    call_static_double_method_v: *const c_void,
    call_static_double_method_a: *const c_void,
    call_static_void_method: *const c_void,
    call_static_void_method_v: *const c_void,
    call_static_void_method_a: *const c_void,
    get_static_field_id: *const c_void,
    get_static_object_field: *const c_void,
    get_static_boolean_field: *const c_void,
    get_static_byte_field: *const c_void,
    get_static_char_field: *const c_void,
    get_static_short_field: *const c_void,
    get_static_int_field: *const c_void,
    get_static_long_field: *const c_void,
    get_static_float_field: *const c_void,
    get_static_double_field: *const c_void,
    set_static_object_field: *const c_void,
    set_static_boolean_field: *const c_void,
    set_static_byte_field: *const c_void,
    set_static_char_field: *const c_void,
    set_static_short_field: *const c_void,
    set_static_int_field: *const c_void,
    set_static_long_field: *const c_void,
    set_static_float_field: *const c_void,
    set_static_double_field: *const c_void,
    new_string: *const c_void,
    get_string_length: *const c_void,
    get_string_chars: *const c_void,
    release_string_chars: *const c_void,
    new_string_utf: NewStringUtf,
}

fn jni_new_string(env: *mut JNIEnv, value: &str) -> *mut c_void {
    let Ok(c_string) = std::ffi::CString::new(value) else {
        return std::ptr::null_mut();
    };
    if env.is_null() {
        return std::ptr::null_mut();
    }
    unsafe {
        let table = *(env as *mut *const JniNativeInterface);
        ((*table).new_string_utf)(env, c_string.as_ptr())
    }
}

fn protect_socket(socket_fd: i32) -> io::Result<()> {
    let cell = SOCKET_PROTECTOR.get().ok_or_else(|| {
        io::Error::new(io::ErrorKind::NotConnected, "VPN socket protector is not configured")
    })?;
    let protector = cell
        .lock()
        .map_err(|_| io::Error::other("VPN socket protector lock is poisoned"))?;
    let protector = protector.as_ref().ok_or_else(|| {
        io::Error::new(io::ErrorKind::NotConnected, "VPN socket protector is unavailable")
    })?;
    let mut env = protector
        .vm
        .attach_current_thread()
        .map_err(|error| io::Error::other(format!("attach JNI thread: {error}")))?;
    let protected = env
        .call_method(
            protector.service.as_obj(),
            "protectSocket",
            "(I)Z",
            &[JValue::Int(socket_fd)],
        )
        .and_then(|value| value.z())
        .map_err(|error| io::Error::other(format!("VpnService.protect JNI call: {error}")))?;
    if protected {
        Ok(())
    } else {
        Err(io::Error::other("VpnService rejected outbound socket protection"))
    }
}

#[cfg(target_os = "android")]
fn connect_target(target: &str) -> io::Result<TcpStream> {
    let mut last_error = None;
    for address in target.to_socket_addrs()? {
        let socket = Socket::new(Domain::for_address(address), Type::STREAM, Some(Protocol::TCP))?;
        protect_socket(socket.as_raw_fd())?;
        match socket.connect(&address.into()) {
            Ok(()) => return Ok(socket.into()),
            Err(error) => last_error = Some(error),
        }
    }
    Err(last_error.unwrap_or_else(|| {
        io::Error::new(io::ErrorKind::AddrNotAvailable, "SOCKS target resolved to no addresses")
    }))
}

#[cfg(not(target_os = "android"))]
fn connect_target(target: &str) -> io::Result<TcpStream> {
    TcpStream::connect(target)
}

#[cfg(target_os = "android")]
fn protect_udp_socket(socket: &UdpSocket) -> io::Result<()> {
    protect_socket(socket.as_raw_fd())
}

#[cfg(not(target_os = "android"))]
fn protect_udp_socket(_socket: &UdpSocket) -> io::Result<()> {
    Ok(())
}

fn run_socks_server(port: u16) {
    let listener = match TcpListener::bind(("127.0.0.1", port)) {
        Ok(listener) => listener,
        Err(_) => {
            RUNNING.store(false, Ordering::SeqCst);
            return;
        }
    };
    let _ = listener.set_nonblocking(true);

    while RUNNING.load(Ordering::SeqCst) {
        match listener.accept() {
            Ok((stream, _)) => {
                let _ = stream.set_read_timeout(Some(Duration::from_secs(15)));
                let _ = stream.set_write_timeout(Some(Duration::from_secs(15)));
                thread::spawn(move || {
                    let _ = handle_socks_client(stream);
                });
            }
            Err(err) if err.kind() == std::io::ErrorKind::WouldBlock => {
                thread::sleep(Duration::from_millis(50));
            }
            Err(_) => thread::sleep(Duration::from_millis(100)),
        }
    }
}

fn handle_socks_client(mut client: TcpStream) -> std::io::Result<()> {
    let mut greeting = [0u8; 258];
    client.read_exact(&mut greeting[..2])?;
    if greeting[0] != 0x05 {
        return Ok(());
    }
    let methods = greeting[1] as usize;
    client.read_exact(&mut greeting[..methods])?;
    client.write_all(&[0x05, 0x00])?;

    let mut header = [0u8; 4];
    client.read_exact(&mut header)?;
    if header[0] != 0x05 {
        client.write_all(&[0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0])?;
        return Ok(());
    }

    match header[1] {
        0x01 => {
            let target = read_socks_target(&mut client, header[3])?;
            let upstream = match connect_target(&target) {
                Ok(stream) => stream,
                Err(error) => {
                    CONNECTION_FAILURES.fetch_add(1, Ordering::Relaxed);
                    return Err(error);
                }
            };
            upstream.set_nodelay(true)?;
            write_socks_success(&mut client, 0)?;
            let result = relay_with_initial_strategy(client, upstream);
            if result.is_err() {
                CONNECTION_FAILURES.fetch_add(1, Ordering::Relaxed);
            }
            result
        }
        0x03 => handle_udp_associate(client, header[3]),
        _ => {
            client.write_all(&[0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0])?;
            Ok(())
        }
    }
}

fn read_socks_target(client: &mut TcpStream, atyp: u8) -> std::io::Result<String> {
    match atyp {
        0x01 => {
            let mut addr = [0u8; 4];
            let mut port = [0u8; 2];
            client.read_exact(&mut addr)?;
            client.read_exact(&mut port)?;
            Ok(format!(
                "{}.{}.{}.{}:{}",
                addr[0],
                addr[1],
                addr[2],
                addr[3],
                u16::from_be_bytes(port)
            ))
        }
        0x03 => {
            let mut len = [0u8; 1];
            client.read_exact(&mut len)?;
            let mut host = vec![0u8; len[0] as usize];
            let mut port = [0u8; 2];
            client.read_exact(&mut host)?;
            client.read_exact(&mut port)?;
            Ok(format!(
                "{}:{}",
                String::from_utf8_lossy(&host),
                u16::from_be_bytes(port)
            ))
        }
        _ => Err(std::io::Error::new(
            std::io::ErrorKind::InvalidInput,
            "unsupported SOCKS address type",
        )),
    }
}

fn write_socks_success(client: &mut TcpStream, port: u16) -> std::io::Result<()> {
    let [hi, lo] = port.to_be_bytes();
    client.write_all(&[0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, hi, lo])
}

fn handle_udp_associate(mut control: TcpStream, atyp: u8) -> std::io::Result<()> {
    let _ = read_socks_target(&mut control, atyp)?;
    let udp = UdpSocket::bind(("127.0.0.1", 0))?;
    let bind_port = udp.local_addr()?.port();
    udp.set_nonblocking(true)?;
    control.set_nonblocking(true)?;
    write_socks_success(&mut control, bind_port)?;

    let mut packet = [0u8; 65_535];
    while RUNNING.load(Ordering::SeqCst) {
        let mut probe = [0u8; 1];
        match control.peek(&mut probe) {
            Ok(0) => break,
            Ok(_) => {}
            Err(err) if err.kind() == std::io::ErrorKind::WouldBlock => {}
            Err(_) => break,
        }

        match udp.recv_from(&mut packet) {
            Ok((read, client_addr)) => {
                let Ok((target, target_port, payload)) = parse_socks_udp_packet(&packet[..read]) else {
                    continue;
                };
                if should_block_udp(target_port, BLOCK_UDP_443.load(Ordering::Relaxed)) {
                    continue;
                }
                let Ok(reply_socket) = udp.try_clone() else {
                    continue;
                };
                let payload = payload.to_vec();
                thread::spawn(move || {
                    let _ = relay_udp_datagram(reply_socket, client_addr, target, payload);
                });
            }
            Err(err) if err.kind() == std::io::ErrorKind::WouldBlock => {
                thread::sleep(Duration::from_millis(10));
            }
            Err(_) => break,
        }
    }

    Ok(())
}

fn parse_socks_udp_packet(packet: &[u8]) -> std::io::Result<(String, u16, &[u8])> {
    if packet.len() < 4 || packet[0] != 0 || packet[1] != 0 || packet[2] != 0 {
        return Err(std::io::Error::new(std::io::ErrorKind::InvalidInput, "invalid SOCKS UDP header"));
    }
    let mut pos = 4;
    let (target, target_port) = match packet[3] {
        0x01 => {
            if packet.len() < pos + 4 + 2 {
                return Err(std::io::Error::new(std::io::ErrorKind::UnexpectedEof, "truncated IPv4 UDP target"));
            }
            let addr = [packet[pos], packet[pos + 1], packet[pos + 2], packet[pos + 3]];
            pos += 4;
            let port = u16::from_be_bytes([packet[pos], packet[pos + 1]]);
            pos += 2;
            (format!("{}.{}.{}.{}:{}", addr[0], addr[1], addr[2], addr[3], port), port)
        }
        0x03 => {
            if packet.len() < pos + 1 {
                return Err(std::io::Error::new(std::io::ErrorKind::UnexpectedEof, "truncated domain UDP target"));
            }
            let len = packet[pos] as usize;
            pos += 1;
            if packet.len() < pos + len + 2 {
                return Err(std::io::Error::new(std::io::ErrorKind::UnexpectedEof, "truncated domain UDP target"));
            }
            let host = String::from_utf8_lossy(&packet[pos..pos + len]);
            pos += len;
            let port = u16::from_be_bytes([packet[pos], packet[pos + 1]]);
            pos += 2;
            (format!("{host}:{port}"), port)
        }
        _ => return Err(std::io::Error::new(std::io::ErrorKind::InvalidInput, "unsupported SOCKS UDP address type")),
    };
    Ok((target, target_port, &packet[pos..]))
}

fn should_block_udp(target_port: u16, block_quic: bool) -> bool {
    block_quic && target_port == 443
}

fn relay_udp_datagram(
    reply_socket: UdpSocket,
    client_addr: SocketAddr,
    target: String,
    payload: Vec<u8>,
) -> std::io::Result<()> {
    let upstream = UdpSocket::bind(("0.0.0.0", 0))?;
    protect_udp_socket(&upstream)?;
    upstream.set_read_timeout(Some(Duration::from_secs(5)))?;
    upstream.send_to(&payload, target)?;

    let mut response = [0u8; 65_535];
    let (read, source) = upstream.recv_from(&mut response)?;
    let packet = build_socks_udp_packet(source, &response[..read]);
    reply_socket.send_to(&packet, client_addr)?;
    Ok(())
}

fn build_socks_udp_packet(source: SocketAddr, payload: &[u8]) -> Vec<u8> {
    let mut packet = Vec::with_capacity(10 + payload.len());
    packet.extend_from_slice(&[0, 0, 0]);
    match source {
        SocketAddr::V4(addr) => {
            packet.push(0x01);
            packet.extend_from_slice(&addr.ip().octets());
            packet.extend_from_slice(&addr.port().to_be_bytes());
        }
        SocketAddr::V6(addr) => {
            packet.push(0x04);
            packet.extend_from_slice(&addr.ip().octets());
            packet.extend_from_slice(&addr.port().to_be_bytes());
        }
    }
    packet.extend_from_slice(payload);
    packet
}

fn relay_with_initial_strategy(mut client: TcpStream, mut upstream: TcpStream) -> std::io::Result<()> {
    let mut initial = [0u8; MAX_INITIAL_BYTES];
    let read = client.read(&mut initial)?;
    if read > 0 {
        let packet = &initial[..read];
        if let Some((split, delay_ms)) = initial_strategy(packet, STRATEGY_PROFILE.load(Ordering::Relaxed)) {
            upstream.write_all(&packet[..split])?;
            if delay_ms > 0 {
                thread::sleep(Duration::from_millis(delay_ms));
            }
            upstream.write_all(&packet[split..])?;
        } else {
            upstream.write_all(packet)?;
        }
    }

    let mut upstream_reader = upstream.try_clone()?;
    let mut client_writer = client.try_clone()?;
    let server_to_client = thread::spawn(move || {
        let _ = std::io::copy(&mut upstream_reader, &mut client_writer);
        let _ = client_writer.shutdown(Shutdown::Write);
    });
    let _ = std::io::copy(&mut client, &mut upstream);
    let _ = upstream.shutdown(Shutdown::Write);
    let _ = server_to_client.join();
    Ok(())
}

fn initial_strategy(packet: &[u8], profile: u8) -> Option<(usize, u64)> {
    match profile {
        PROFILE_COMPATIBLE => tls_sni_split_position(packet)
            .or_else(|| http_host_split_position(packet))
            .map(|split| (split, 0)),
        PROFILE_AGGRESSIVE => tls_sni_start_split_position(packet)
            .or_else(|| http_host_start_split_position(packet))
            .map(|split| (split, 35)),
        PROFILE_ZAPTRET2 => zaptret2_split_position(packet).map(|split| (split, 40)),
        PROFILE_CUSTOM => {
            if packet.len() < 2 {
                None
            } else {
                let split = CUSTOM_SPLIT.load(Ordering::Relaxed).clamp(1, packet.len() - 1);
                Some((split, CUSTOM_DELAY_MS.load(Ordering::Relaxed)))
            }
        }
        _ => tls_sni_split_position(packet)
            .or_else(|| http_host_split_position(packet))
            .map(|split| (split, 12)),
    }
}

/// Zaptret2 ignores SNI/Host parsing entirely and splits right after the first
/// TCP payload byte, mirroring the classic zapret raw-segmentation technique
/// that defeats DPI engines which buffer on full-record boundaries rather than
/// on the very first bytes of a stream.
fn zaptret2_split_position(packet: &[u8]) -> Option<usize> {
    if packet.len() < 2 {
        None
    } else {
        Some(1)
    }
}

pub fn http_host_split_position(buf: &[u8]) -> Option<usize> {
    let (value_start, line_end) = http_host_value_bounds(buf)?;
    Some(value_start + ((line_end - value_start).max(1) / 2))
}

fn http_host_start_split_position(buf: &[u8]) -> Option<usize> {
    let (value_start, line_end) = http_host_value_bounds(buf)?;
    let mut host_start = value_start;
    while host_start < line_end && matches!(buf[host_start], b' ' | b'\t') {
        host_start += 1;
    }
    if host_start >= line_end {
        return None;
    }
    Some(host_start + usize::from(line_end - host_start > 1))
}

fn http_host_value_bounds(buf: &[u8]) -> Option<(usize, usize)> {
    let header_end = buf.windows(4).position(|w| w == b"\r\n\r\n")?;
    if header_end > 8192 {
        return None;
    }
    let headers = &buf[..header_end + 2];
    let lower = ascii_lowercase(headers);
    let marker = b"\r\nhost:";
    let start = lower.windows(marker.len()).position(|w| w == marker)?;
    let value_start = start + marker.len();
    let line_end = lower[value_start..]
        .windows(2)
        .position(|w| w == b"\r\n")?
        + value_start;
    Some((value_start, line_end))
}

pub fn tls_sni_split_position(buf: &[u8]) -> Option<usize> {
    let parsed = parse_tls_client_hello(buf).ok()?;
    parsed.sni_mid
}

fn tls_sni_start_split_position(buf: &[u8]) -> Option<usize> {
    let parsed = parse_tls_client_hello(buf).ok()?;
    Some(parsed.sni_start + usize::from(parsed.sni_end - parsed.sni_start > 1))
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TlsClientHello {
    pub sni: String,
    pub sni_start: usize,
    pub sni_mid: Option<usize>,
    pub sni_end: usize,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ParseError {
    Truncated,
    Invalid,
    TooLarge,
    MissingSni,
}

pub fn parse_tls_client_hello(buf: &[u8]) -> Result<TlsClientHello, ParseError> {
    if buf.len() > MAX_INITIAL_BYTES {
        return Err(ParseError::TooLarge);
    }
    if buf.len() < 5 {
        return Err(ParseError::Truncated);
    }
    if buf[0] != 0x16 {
        return Err(ParseError::Invalid);
    }
    let record_len = u16::from_be_bytes([buf[3], buf[4]]) as usize;
    if buf.len() < 5 + record_len {
        return Err(ParseError::Truncated);
    }
    let record = &buf[5..5 + record_len];
    if record.len() < 4 || record[0] != 0x01 {
        return Err(ParseError::Invalid);
    }
    let handshake_len = ((record[1] as usize) << 16) | ((record[2] as usize) << 8) | record[3] as usize;
    if record.len() < 4 + handshake_len {
        return Err(ParseError::Truncated);
    }

    let mut pos = 4;
    require(record, pos, 2 + 32)?;
    pos += 2 + 32;
    let session_len = *record.get(pos).ok_or(ParseError::Truncated)? as usize;
    pos += 1;
    require(record, pos, session_len + 2)?;
    pos += session_len;
    let cipher_len = u16::from_be_bytes([record[pos], record[pos + 1]]) as usize;
    pos += 2;
    require(record, pos, cipher_len + 1)?;
    pos += cipher_len;
    let compression_len = record[pos] as usize;
    pos += 1;
    require(record, pos, compression_len + 2)?;
    pos += compression_len;
    let extensions_len = u16::from_be_bytes([record[pos], record[pos + 1]]) as usize;
    pos += 2;
    require(record, pos, extensions_len)?;
    let extensions_end = pos + extensions_len;

    while pos + 4 <= extensions_end {
        let ext_type = u16::from_be_bytes([record[pos], record[pos + 1]]);
        let ext_len = u16::from_be_bytes([record[pos + 2], record[pos + 3]]) as usize;
        pos += 4;
        require(record, pos, ext_len)?;
        if ext_type == 0x0000 {
            return parse_sni_extension(buf, 5 + pos, &record[pos..pos + ext_len]);
        }
        pos += ext_len;
    }

    Err(ParseError::MissingSni)
}

fn parse_sni_extension(original: &[u8], absolute_extension_start: usize, ext: &[u8]) -> Result<TlsClientHello, ParseError> {
    if ext.len() < 2 {
        return Err(ParseError::Truncated);
    }
    let list_len = u16::from_be_bytes([ext[0], ext[1]]) as usize;
    if ext.len() < 2 + list_len {
        return Err(ParseError::Truncated);
    }
    let mut pos = 2;
    while pos + 3 <= 2 + list_len {
        let name_type = ext[pos];
        let name_len = u16::from_be_bytes([ext[pos + 1], ext[pos + 2]]) as usize;
        pos += 3;
        if pos + name_len > ext.len() {
            return Err(ParseError::Truncated);
        }
        if name_type == 0 {
            let start = absolute_extension_start + pos;
            let end = start + name_len;
            let sni = std::str::from_utf8(&original[start..end]).map_err(|_| ParseError::Invalid)?;
            let mid = if name_len > 1 {
                Some(start + name_len / 2)
            } else {
                Some(start)
            };
            return Ok(TlsClientHello {
                sni: sni.to_owned(),
                sni_start: start,
                sni_mid: mid,
                sni_end: end,
            });
        }
        pos += name_len;
    }
    Err(ParseError::MissingSni)
}

fn require(buf: &[u8], start: usize, len: usize) -> Result<(), ParseError> {
    if start.checked_add(len).is_some_and(|end| end <= buf.len()) {
        Ok(())
    } else {
        Err(ParseError::Truncated)
    }
}

fn ascii_lowercase(buf: &[u8]) -> Vec<u8> {
    buf.iter().map(|b| b.to_ascii_lowercase()).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn http_host_split_is_middle_of_host() {
        let request = b"GET / HTTP/1.1\r\nHost: example.com\r\nUser-Agent: test\r\n\r\n";
        let split = http_host_split_position(request).expect("host split");
        assert!(split > 18);
        assert!(split < 31);
    }

    #[test]
    fn http_host_rejects_incomplete_headers() {
        assert_eq!(http_host_split_position(b"GET / HTTP/1.1\r\nHost: example.com"), None);
    }

    #[test]
    fn strategy_profiles_change_http_split_and_delay() {
        let request = b"GET / HTTP/1.1\r\nHost: example.com\r\n\r\n";
        let (compatible_split, compatible_delay) =
            initial_strategy(request, PROFILE_COMPATIBLE).expect("compatible strategy");
        let (balanced_split, balanced_delay) =
            initial_strategy(request, PROFILE_BALANCED).expect("balanced strategy");
        let (aggressive_split, aggressive_delay) =
            initial_strategy(request, PROFILE_AGGRESSIVE).expect("aggressive strategy");

        assert_eq!(compatible_split, balanced_split);
        assert_eq!(compatible_delay, 0);
        assert_eq!(balanced_delay, 12);
        assert!(aggressive_split < balanced_split);
        assert_eq!(aggressive_delay, 35);
    }

    #[test]
    fn zaptret2_profile_splits_after_first_byte() {
        let request = b"GET / HTTP/1.1\r\nHost: example.com\r\n\r\n";
        let (split, delay) = initial_strategy(request, PROFILE_ZAPTRET2).expect("zaptret2 strategy");
        assert_eq!(split, 1);
        assert_eq!(delay, 40);
        assert_eq!(initial_strategy(&[0x16], PROFILE_ZAPTRET2), None);
    }

    #[test]
    fn custom_profile_uses_configured_split_and_delay() {
        let request = b"GET / HTTP/1.1\r\nHost: example.com\r\n\r\n";
        CUSTOM_SPLIT.store(7, Ordering::SeqCst);
        CUSTOM_DELAY_MS.store(99, Ordering::SeqCst);
        let (split, delay) = initial_strategy(request, PROFILE_CUSTOM).expect("custom strategy");
        assert_eq!(split, 7);
        assert_eq!(delay, 99);

        CUSTOM_SPLIT.store(usize::MAX, Ordering::SeqCst);
        let (clamped_split, _) = initial_strategy(request, PROFILE_CUSTOM).expect("custom strategy");
        assert_eq!(clamped_split, request.len() - 1);
    }

    #[test]
    fn tls_parser_rejects_truncated_record() {
        assert_eq!(parse_tls_client_hello(&[0x16, 0x03, 0x01, 0x00, 0x20]), Err(ParseError::Truncated));
    }

    #[test]
    fn tls_parser_rejects_non_handshake_record() {
        let packet = [0x17, 0x03, 0x03, 0x00, 0x00];
        assert_eq!(parse_tls_client_hello(&packet), Err(ParseError::Invalid));
    }

    #[test]
    fn socks_udp_packet_parses_ipv4_target() {
        let packet = [0, 0, 0, 1, 1, 1, 1, 1, 0, 53, 0xAA, 0xBB];
        let (target, port, payload) = parse_socks_udp_packet(&packet).expect("udp target");
        assert_eq!(target, "1.1.1.1:53");
        assert_eq!(port, 53);
        assert_eq!(payload, &[0xAA, 0xBB]);
    }

    #[test]
    fn quic_policy_blocks_only_udp_443_when_enabled() {
        assert!(should_block_udp(443, true));
        assert!(!should_block_udp(443, false));
        assert!(!should_block_udp(53, true));
    }

    #[test]
    fn socks_udp_response_uses_ipv4_header() {
        let source: SocketAddr = "8.8.8.8:53".parse().expect("socket addr");
        let packet = build_socks_udp_packet(source, &[1, 2, 3]);
        assert_eq!(&packet[..10], &[0, 0, 0, 1, 8, 8, 8, 8, 0, 53]);
        assert_eq!(&packet[10..], &[1, 2, 3]);
    }
}
