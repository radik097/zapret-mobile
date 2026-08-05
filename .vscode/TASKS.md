# TASKS.md

## 2026-08-05 - Multi-file LLM agent basis

Проблема - все инструкции агента и техническое ТЗ находились в одном большом файле, из-за чего модели пришлось бы постоянно перечитывать лишний контекст.

Причина - первоначальная настройка была сделана в single-file режиме через `INSTRUCTION.md`.

Решение - разделить базис агента на навигационный индекс, роль, память, задачи, workflow, BLACKBOX и отдельный проектный бриф.

Что сделано - созданы/обновлены файлы `INSTRUCTION.md`, `AGENT.md`, `MEMORY.md`, `TASKS.md`, `WORKFLOW.md`, `BLACKBOX.md`, `PROJECT_BRIEF.md`, `.vscode/TASKS.md`.

Дата и время - 2026-08-05 23:23:05 +10:00

## 2026-08-05 - Development start prompt

Проблема - агенту нужен короткий стартовый промпт для начала разработки, чтобы не тратить токены на обсуждение и не начинать без проверки Windows-среды.

Причина - полный проектный бриф большой и содержит старое стек-требование с Kotlin, тогда как текущая команда пользователя требует Rust, не Kotlin, и использование уже установленных системных ресурсов.

Решение - создать `START_PROMPT.md` и подключить его в навигацию агента как обязательный файл перед стартом разработки.

Что сделано - добавлен `START_PROMPT.md`; обновлены `INSTRUCTION.md`, `MEMORY.md`, `TASKS.md`, `WORKFLOW.md` и `.vscode/TASKS.md`.

Дата и время - 2026-08-05 23:26:00 +10:00

## 2026-08-06 - Rust-first Android skeleton and debug APK

Проблема - в `D:\Android\VPN_app` были только проектные инструкции и не было Android-проекта, SDK toolchain, Gradle wrapper или APK.

Причина - предыдущий этап подготовил агентные файлы, но не выполнял разработку приложения и не проверял Android/Rust сборку.

Решение - установить недостающую локальную Android/Rust build-среду, создать Rust-first Android skeleton, связать Gradle с `cargo-ndk`, собрать и проверить debug APK.

Что сделано - установлен JDK 21, Android SDK в `D:\Android\Sdk`, platform 36, Build Tools 36.1.0, NDK 28.2.13676358, CMake 3.31.6, Gradle 9.6.1 wrapper, Rust Android targets и `cargo-ndk`; добавлены `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/`, `native-engine/rust/zapret_engine/`, `scripts/build-debug.ps1`, docs, CI workflow; реализованы Java `MainActivity`, `ZapretVpnService`, JNI wrapper и Rust SOCKS/DPI skeleton с HTTP Host/TLS ClientHello parser tests.

Дата и время - 2026-08-06 00:04:30 +10:00

Проверка - `cargo test --manifest-path native-engine/rust/zapret_engine/Cargo.toml` прошёл 4/4; `.\gradlew.bat test` прошёл; `.\gradlew.bat lintDebug` прошёл с 0 errors и 2 warnings по API 37; `.\gradlew.bat assembleDebug` прошёл; `.\gradlew.bat clean test assembleDebug` прошёл; `scripts\build-debug.ps1` прошёл.

Артефакт - `D:\Android\VPN_app\app\build\outputs\apk\debug\app-debug.apk`, 1,666,443 bytes, SHA-256 `A9E2FE840933750017AED4BAFD1FFE335C1D03FA578E151F1B286D4B6F7A9BF4`.

Ограничения - `adb devices` не нашёл подключённых устройств или эмуляторов, поэтому установка, запуск, выдача VPN-разрешения и фактическое создание TUN на устройстве ещё не проверены; TUN-to-SOCKS bridge и полноценный MVP ещё не реализованы.

## 2026-08-06 - Emulator runtime smoke test

Проблема - debug APK был собран, но не было локального Android runtime-окружения для проверки установки, запуска, VPN permission и TUN.

Причина - до установки emulator/system image `adb devices` не показывал доступный эмулятор; без runtime-проверки нельзя подтверждать Android `VpnService` поведение.

Решение - установить Android Emulator и `system-images;android-36;google_apis;x86_64`, создать AVD `zapret_api36_x86_64`, запустить его headless и автоматизировать smoke-test через ADB/UI Automator taps.

Что сделано - добавлен `scripts/emulator-smoke.ps1`; создан AVD `zapret_api36_x86_64`; APK установлен на `emulator-5554`; Activity запущена; VPN permission dialog подтверждён; проверены foreground `ZapretVpnService`, `tun0` с `10.71.0.1`, `dumpsys connectivity` с `VPN:dev.zapret.mobile`; затем сервис остановлен и TUN очищен.

Дата и время - 2026-08-06 00:15:30 +10:00

Проверка - `powershell -ExecutionPolicy Bypass -File scripts\emulator-smoke.ps1` прошёл с сообщением `Emulator smoke test passed: install, launch, permission, TUN create, foreground service, stop.`

Ограничения - smoke-test подтверждает только Android shell/VpnService lifecycle; настоящий TUN-to-SOCKS relay ещё не реализован, поэтому прохождение пользовательского TCP-трафика через DPI engine пока не подтверждается.

## 2026-08-06 - Maestro automation and TUN-to-SOCKS bridge integration

Проблема - приложение создавало TUN и локальный Rust SOCKS listener, но не было packet bridge между Android `VpnService` fd и SOCKS/DPI engine; также отсутствовал воспроизводимый UI smoke через Maestro с JUnit/артефактами.

Причина - первый runtime smoke проверял lifecycle через ADB taps, а не рекомендованный агентный слой Maestro; TUN fd оставался не подключённым к SOCKS engine.

Решение - собрать и встроить `hev-socks5-tunnel` как native TUN-to-SOCKS bridge, добавить Java JNI wrapper, запуск bridge на duplicate TUN fd, починить clean stop через закрытие detached fd перед native shutdown, добавить Maestro start/stop flows и усилить smoke-скрипт ожиданием стабильного ADB transport.

Что сделано - добавлены `.maestro/zapret-smoke.yaml`, `.maestro/zapret-stop.yaml`, `scripts/maestro-smoke.ps1`, `scripts/build-hev-socks5.ps1`, `app/src/main/java/hev/htproxy/TProxyService.java`, `app/src/main/java/dev/zapret/mobile/Tun2SocksBridge.java`; обновлены `app/build.gradle.kts`, `ZapretVpnService.java`, `native-engine/rust/zapret_engine/src/lib.rs`, `docs/ARCHITECTURE.md`, `docs/THIRD_PARTY_LICENSES.md`, `TASKS.md`.

Дата и время - 2026-08-06 00:45:39 +10:00

Проверка - `cargo test --manifest-path native-engine/rust/zapret_engine/Cargo.toml` прошёл 6/6; `.\gradlew.bat test lintDebug assembleDebug` прошёл; `powershell -ExecutionPolicy Bypass -File scripts\maestro-smoke.ps1` прошёл. JUnit: `build/test-artifacts/maestro-smoke/maestro-junit.xml` и `build/test-artifacts/maestro-smoke/maestro-stop-junit.xml` оба `failures="0"`. Active artifacts показывают foreground `ZapretVpnService`, `tun0` и `10.71.0.1`; stopped artifacts показывают `(nothing)` для сервиса и отсутствие `tun0/10.71.0.1`.

Артефакт - `D:\Android\VPN_app\app\build\outputs\apk\debug\app-debug.apk`, 2,432,359 bytes, SHA-256 `2C5A12F945F33F27008B5F2D1AECE5B7BD381EE9F3DF8772C7E1A2F2CCCEE28C`.

Ограничения - bridge теперь встроен и lifecycle подтверждён, но отдельный deterministic user-app TCP test через `VpnService TUN -> hev-socks5-tunnel -> Rust SOCKS/DPI engine -> physical network` ещё не добавлен; HAR/PCAP capture, DPI simulator, QUIC policy, app/profile UI и физическое устройство остаются следующими задачами.

## 2026-08-06 - Deterministic TCP traffic proof through VPN bridge

Проблема - после интеграции `hev-socks5-tunnel` был подтверждён lifecycle bridge, но не было отдельного доказательства, что трафик другого приложения реально проходит через `VpnService TUN -> hev-socks5-tunnel -> Rust SOCKS/DPI engine`.

Причина - предыдущий Maestro smoke проверял `tun0`, foreground service и ConnectivityService, но не генерировал TCP-трафик от отдельного UID, не исключённого из VPN.

Решение - добавить отдельный debug APK `dev.zapret.testclient`, который делает HTTP GET к host endpoint `http://10.0.2.2:18080/probe`, и автоматизировать полный proof в `scripts/traffic-proof.ps1` с локальным Python HTTP server, Maestro start/stop, logcat и service/interface dumps.

Что сделано - добавлен модуль `test-client`; обновлён `settings.gradle.kts`; добавлен `scripts/traffic-proof.ps1`; обновлены `docs/ARCHITECTURE.md` и `TASKS.md`.

Дата и время - 2026-08-06 00:58:32 +10:00

Проверка - `.\gradlew.bat test lintDebug assembleDebug :test-client:assembleDebug` прошёл; `powershell -ExecutionPolicy Bypass -File scripts\traffic-proof.ps1` прошёл с `Traffic proof passed for http://10.0.2.2:18080/probe`. В `build/test-artifacts/traffic-proof/logcat.txt` есть `ZAPRET_TEST_CLIENT: result=200 body=zapret-proof`; host server log содержит `GET /probe HTTP/1.1` 200; start/stop JUnit имеют `failures="0"`; stopped artifacts показывают `(nothing)` для `ZapretVpnService` и отсутствие активного `tun0`.

Артефакты - основной APK `D:\Android\VPN_app\app\build\outputs\apk\debug\app-debug.apk`, 2,432,359 bytes, SHA-256 `2C5A12F945F33F27008B5F2D1AECE5B7BD381EE9F3DF8772C7E1A2F2CCCEE28C`; test-client APK `D:\Android\VPN_app\test-client\build\outputs\apk\debug\test-client-debug.apk`, 875,010 bytes, SHA-256 `1BC1BF3692A1259F61095C7BE7FAFFA9CF2F3563925FBA24C1A145A8B4551C92`.

Ограничения - этот proof покрывает TCP/IPv4 к deterministic host endpoint; HAR/PCAP capture, DPI simulator, QUIC policy, app/profile UI, production socket protection callback и физическое устройство ещё не закрыты.

## 2026-08-06 - Deterministic DPI simulator proof

Проблема - был proof прохождения TCP-трафика через VPN bridge, но не было локального DPI-сценария, который доказывает наблюдаемое split-поведение Rust SOCKS/DPI engine на HTTP Host.

Причина - `test-client` делал только обычный `HttpURLConnection` к host endpoint; для проверки `Host: blocked.example` нужен raw HTTP-запрос с управляемым authority и сервер, который смотрит первый TCP chunk.

Решение - расширить `dev.zapret.testclient` raw HTTP режимом и добавить локальный Python DPI simulator, который блокирует unsplit Host, но разрешает запрос, если полный `blocked.example` отсутствует в первом chunk.

Что сделано - обновлён `test-client/src/main/java/dev/zapret/testclient/TestClientActivity.java`; добавлены `tools/dpi_http_simulator.py` и `scripts/dpi-proof.ps1`; обновлены `docs/ARCHITECTURE.md`, `TASKS.md`, `MEMORY.md`.

Дата и время - 2026-08-06 01:10:28 +10:00

Проверка - `cargo test --manifest-path native-engine/rust/zapret_engine/Cargo.toml` прошёл 6/6; `.\gradlew.bat test lintDebug assembleDebug :test-client:assembleDebug` прошёл; `powershell -ExecutionPolicy Bypass -File scripts\dpi-proof.ps1` прошёл с `DPI proof passed on port 18081`; `powershell -ExecutionPolicy Bypass -File scripts\traffic-proof.ps1` повторно прошёл.

Артефакты - `D:\Android\VPN_app\build\test-artifacts\dpi-proof\dpi-report.json` содержит `decision=allowed_split`, `passed=true`, `chunk_count=2`, первый chunk `GET /probe HTTP/1.1\r\nHost: blocked`, полный запрос `Host: blocked.example`; `logcat.txt` содержит `raw_result=200 body=dpi-split-proof`; start/stop JUnit имеют `failures="0"`; stopped artifacts показывают `(nothing)` для `ZapretVpnService`.

Ограничения - это deterministic HTTP DPI proof без HAR/PCAP и без HTTPS MITM; остаются HAR/PCAP capture, QUIC policy, app/profile UI, production socket protection callback и физическое устройство.

## 2026-08-06 - Emulator PCAP capture proof

Проблема - DPI proof сохранял JSON/logcat/JUnit, но не было реального packet capture артефакта, пригодного для последующего анализа в Wireshark/tcpdump-цепочке.

Причина - локально не найден `tshark`/`dumpcap`; доступный штатный путь без установки тяжёлого ПО - Android Emulator `-tcpdump`, который включается при запуске AVD.

Решение - добавить отдельный wrapper `scripts/dpi-proof-pcap.ps1`, который останавливает текущий `emulator-5554`, запускает AVD `zapret_api36_x86_64` с `-tcpdump`, выполняет `scripts/dpi-proof.ps1`, затем останавливает эмулятор для flush `.pcap`.

Что сделано - добавлен `scripts/dpi-proof-pcap.ps1`; обновлены `docs/ARCHITECTURE.md`, `TASKS.md`, `MEMORY.md`.

Дата и время - 2026-08-06 01:17:20 +10:00

Проверка - `powershell -ExecutionPolicy Bypass -File scripts\dpi-proof-pcap.ps1` прошёл; вложенный DPI proof прошёл; создан `D:\Android\VPN_app\build\test-artifacts\dpi-proof-pcap\emulator-network.pcap`, 30,007 bytes; первые байты `D4 C3 B2 A1` подтверждают classic PCAP header.

Артефакты - `D:\Android\VPN_app\build\test-artifacts\dpi-proof-pcap\emulator-network.pcap`; рядом скопированы `dpi-report.json` и `logcat.txt` последнего DPI proof.

Ограничения - это emulator-level PCAP, не HAR и не HTTPS MITM; `tshark/dumpcap` локально не найдены, `pktmon` есть; после proof эмулятор остановлен, `adb devices` показывает только физическое/network device `adb-53271JEKB00683-G83QXW._adb-tls-connect._tcp`, на него APK не устанавливался.

## 2026-08-06 - DPI simulator harness hardening and profile rollback

Проблема - попытка добавить persisted strategy profiles и JNI strategy flags собрала APK, но сломала runtime DPI proof; после отката приложение восстановилось, однако сам `dpi_http_simulator.py` выявил отдельную нестабильность: он мог принять пустое/no-data TCP-подключение и завершиться до реального raw HTTP запроса.

Причина - profile/JNI изменение не было подтверждено runtime proof, а DPI simulator обслуживал только первое входящее соединение на `127.0.0.1:18081`.

Решение - откатить profile/JNI/app-code изменение к последнему рабочему состоянию и усилить только test harness: simulator теперь игнорирует no-data соединения, ждёт реальный HTTP bytes до общего deadline и пишет отчёт по финальному состоянию.

Что сделано - откат профилей из `MainActivity.java`, `NativeZapretEngine.java`, `ZapretVpnService.java`, `strings.xml` и `native-engine/rust/zapret_engine/src/lib.rs`; удалён добавленный `ProfileSettings.java`; обновлён `tools/dpi_http_simulator.py`; добавлен полный отчёт `docs/WORK_REPORT_2026-08-06.md`; добавлен `.gitignore`.

Дата и время - 2026-08-06 01:34:05 +10:00

Проверка - `cargo test --manifest-path native-engine\rust\zapret_engine\Cargo.toml` прошёл 6/6; `.\gradlew.bat test lintDebug assembleDebug :test-client:assembleDebug` прошёл; `python -m py_compile tools\dpi_http_simulator.py` прошёл; `powershell -ExecutionPolicy Bypass -File scripts\dpi-proof.ps1` прошёл; `powershell -ExecutionPolicy Bypass -File scripts\traffic-proof.ps1` прошёл.

Ограничения - profile/settings UI, QUIC policy и HAR export остаются в backlog; app-code профильная правка не зафиксирована, потому что runtime proof её не подтвердил.
