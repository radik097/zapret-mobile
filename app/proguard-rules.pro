-keep class dev.zapret.mobile.NativeZapretEngine { *; }
-keepclassmembers class dev.zapret.mobile.ZapretVpnService {
    public boolean protectSocket(int);
}
