# MobileClientKit

Minecraft Bedrock (Android) အတွက် **quality-of-life client** — MarkClient ရဲ့ feature စာရင်းနဲ့ UI ပုံစံကို ရည်ညွှန်းပြီး mobile အတွက် ပြန်ရေးထားတာပါ။

| Folder | အကြောင်း |
|---|---|
| `client-sdk/` | **Android Library (AAR)** — Kotlin (click menu, HUD, modules) + C++ NDK (eglSwapBuffers hook, pattern scanner, C API) |
| `launcher/` | **Minecraft PE / Patch** ရွေးပြီး Play နှိပ်တဲ့ app |
| `tools/patch_apk.sh` | Minecraft APK ထဲကို client ထည့်ပေးတဲ့ script (PC / Termux) |

> MarkClient (AdriMark LLC) နဲ့ ဆက်စပ်မှု မရှိပါ။ သူတို့ရဲ့ code / logo / asset ကို မသုံးထားပါ (သူတို့က "All rights reserved")။
> Mojang / Microsoft ရဲ့ official product လည်း မဟုတ်ပါ။

## 1. Build

```bash
gradle wrapper --gradle-version 8.9      # Android Studio နဲ့ ဖွင့်ရင် မလိုပါ
./gradlew :client-sdk:assembleRelease    # -> client-sdk/build/outputs/aar/client-sdk-release.aar
./gradlew :launcher:assembleDebug        # -> launcher/build/outputs/apk/debug/launcher-debug.apk
```

လိုအပ်တာ: JDK 17 · Android SDK 35 · NDK + CMake 3.22 (SDK Manager ကနေ install)။

> ⚠️ ဒီ source ကို ရေးတဲ့နေရာမှာ Android SDK / NDK / Gradle / internet မရှိလို့ **Kotlin ကို compile မလုပ်နိုင်ခဲ့ပါ**
> (C++ ကို stub header နဲ့ syntax စစ်ထားတယ်)။ ပထမ build မှာ error ပေါ်ရင် log ကို ပြပါ၊ ပြင်ပေးမယ်။

## 2. အလုပ်လုပ်တဲ့ features

| Category | Module | အကြောင်း |
|---|---|---|
| HUD | **FPS** | native `eglSwapBuffers` hook နဲ့ တိတိကျကျ တိုင်း (hook မရရင် Choreographer) |
| HUD | **CPS** | screen tap / စက္ကန့် |
| HUD | **Clock · Battery · Memory** | device အချိန် · battery % · game process RAM |
| HUD | **Server Ping** | RakNet ping (server address ကို module settings မှာ ထည့်) |
| HUD | **Crosshair** | အရောင် / ပုံစံ / အရွယ်အစား ပြောင်းလို့ရတဲ့ crosshair |
| HUD | **Reference Image** | ပုံ / GIF / animated WebP ကို ဂိမ်းပေါ်မှာ ပြ (build reference) |
| Visual | **Screen Filter** | screen ကို အရောင်ခြယ် (night / warm filter) |
| Client | Menu button · popups · accent colour | ☰ ခလုတ် ရွှေ့ / အရွယ် / opacity |
| — | **HUD Editor** | HUD element တွေကို ဆွဲရွှေ့ |
| — | **Profiles** | settings တွေကို သိမ်း / ပြန်ခေါ် |
| — | **ServerPolicy** | server / world က module တစ်ခုချင်း ခွင့်ပြု / ပိတ် (JSON) |

Reference Image: ပုံတွေကို `Android/media/<package>/MobileClient/reference/` ထဲ ထည့်ပါ
(လမ်းကြောင်းအတိအကျကို module settings ထဲမှာ ပြပေးတယ်)။

## 3. သုံးနည်း

### A) Patch လုပ်ထားတဲ့ Minecraft (အလိုအလျောက်)
AAR manifest ထဲက `ClientInitProvider` က process စတာနဲ့ client ကို စတင်ပေးတယ် — code ရေးစရာ မလို။

### B) Kotlin / Java app
```kotlin
// build.gradle.kts
implementation(files("libs/client-sdk-release.aar"))
implementation("com.bytedance.android:shadowhook:1.0.9")
implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
```
```kotlin
MobileClient.init(activity)                 // manual attach
MobileClient.openMenu()                     // click menu ဖွင့်
MobileClient.setModuleEnabled("fps", true)
```
manual attach သုံးချင်ရင် manifest မှာ
`<meta-data android:name="mobileclient.auto_attach" android:value="false" tools:replace="android:value"/>`

### C) C++ NDK
```cpp
#include <mobileclient.h>
mc_attach(env, activity);
mc_open_menu();
int fps = mc_get_fps();
```
CMake (prefab): `find_package(client-sdk REQUIRED CONFIG)` + `target_link_libraries(app client-sdk::mobileclient)`
(package နာမည်က Gradle module နာမည်။ မရရင် AAR ထဲက `prefab/prefab.json` ကို ကြည့်ပါ)

### D) Module အသစ်ထည့်
```kotlin
class HelloModule : TextHudModule("hello", "Hello", "demo", 0.5f, 0.1f) {
    override fun text() = "Hello"
}
MobileClient.registerModule(HelloModule())
```

### E) ကိုယ့်ဂိမ်း hook (C++)
```cpp
uintptr_t addr = mc_find_pattern("libminecraftpe.so", "FD 7B ?? A9 ?? ?? ?? ??"); // ကိုယ့် version ရဲ့ signature
void* stub = mc_hook_function((void*)addr, (void*)my_proxy, (void**)&orig);
```

## 4. Minecraft ကို Patch လုပ်နည်း

1. ကိုယ့် device ကနေ APK ထုတ်: `adb shell pm path com.mojang.minecraftpe` → `adb pull .../base.apk`
2. `ANDROID_HOME` သတ်မှတ်ပြီး (apktool လိုတယ်):
   `tools/patch_apk.sh base.apk client-sdk-release.aar shadowhook-1.0.9.aar out.apk`
3. ⚠️ Original ကို uninstall မလုပ်ခင် world တွေ backup:
   `/sdcard/Android/data/com.mojang.minecraftpe/files/games/com.mojang`
4. `out.apk` ကို install (split APK ဆိုရင် split အားလုံးကို key တူနဲ့ sign ပြီး `adb install-multiple`)
5. Launcher ကနေ **Patch** ရွေးပြီး Play။ ဂိမ်းထဲမှာ ☰ ခလုတ်ကို နှိပ်ရင် click menu ပွင့်မယ်။

**PE / Patch ဆိုတာ** — install လုပ်ထားတဲ့ Minecraft တစ်ခုတည်းကို launcher က Intent extra (`mobileclient.mode`) နဲ့ စတင်တာပါ။
`Minecraft PE` = client ပိတ်ထားမယ်၊ `Patch` = client ဖွင့်မယ်။

Patched APK ကို ကိုယ့် copy မှာပဲ သုံးပါ၊ မဖြန့်ပါနဲ့။ Google Play ရဲ့ license / integrity စစ်ဆေးမှုတွေကြောင့်
version တချို့မှာ patched APK မစတင်နိုင်တာ ဖြစ်နိုင်ပါတယ်။

## 5. ရိုးရိုးသားသား ကန့်သတ်ချက်

- **MarkClient က Windows `.dll` injection** ပါ။ သူတို့ FAQ မှာ mobile မရှိသေးဘူးလို့ ကိုယ်တိုင်ပြောထားတယ်။
  Zoom, Toggle Sprint, Minimap, Waypoints, Hitboxes, Light Levels, Freecam, Replay, Schematics တို့က game ရဲ့ internal
  function / memory ကို hook လုပ်ရတာမို့ `libminecraftpe.so` version တစ်ခုချင်းစီအတွက် signature / offset လိုတယ်။
  binary မရှိလို့ ဒီမှာ **မထည့်နိုင်ပါ**။ `mc_find_pattern` + `mc_hook_function` ပါပြီးသားမို့ ကိုယ့် version အတွက် ထပ်ထည့်နိုင်တယ်။
- Multiplayer မှာ cheat ဖြစ်နိုင်တဲ့ module (killaura, reach ...) မထည့်ပါ — MarkClient လိုပဲ legit gameplay အတွက်ပဲ။
- CPS: NativeActivity သုံးတဲ့ build မှာ ပထမလက်ချောင်း tap ကိုပဲ ရတယ်၊ GameActivity / view-based ဆိုရင် လက်ချောင်းအားလုံး ရတယ်။
- UI: MarkClient ရဲ့ in-game menu ကို မမြင်ရလို့ website ကယူတဲ့ brand blue `#3A9BDC`၊ category (Visual / HUD / Client)၊
  module card၊ Profiles၊ ON/OFF popup၊ scroll နေရာမှတ်ထားခြင်း (changelog မှာ ဖော်ပြထား) ကို အခြေခံထားတာပါ။
  screenshot ပေးရင် `ui/Theme.kt` နဲ့ `ui/ClickMenu.kt` ကို ပိုတူအောင် ညှိပေးမယ်။
