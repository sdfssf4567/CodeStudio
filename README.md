# CodeStudio 📱💻

**ویرایشگر کد وب برای اندروید — شبیه VSCode، مخصوص موبایل**

CodeStudio یک ویرایشگر کد کامل برای توسعه وب (HTML / CSS / JavaScript / Bootstrap 5) است که با **Kotlin** و هسته **CodeMirror 6** (به‌صورت Asset محلی — کاملاً آفلاین) ساخته شده است.

## ✨ قابلیت‌ها

| قابلیت | توضیح |
|---|---|
| 🎨 Syntax Highlighting | رنگ‌بندی دقیق مثل VSCode Dark+ برای HTML، CSS، JS |
| 🧠 Autocomplete هوشمند | تگ‌ها، attributeها، propertyهای CSS، کلمات کلیدی JS + **۱۷۵۰+ کلاس Bootstrap 5** (در `class="..."` و `classList.add`) |
| ⚡ Emmet ساده | تایپ `!` در فایل HTML → قالب کامل HTML5 + اسنیپت‌های آماده (`link:bs`, `card:bs`, `grid:bs`, `nav:bs`, ...) |
| 👁 Live Preview | رندر زنده با WebView — لینک خودکار CSS/JS/تصاویر پروژه + دکمه تزریق CDN بوت‌استرپ |
| 🗂 سیستم تب | باز کردن چند فایل همزمان، سوییچ، بستن با ضربدر |
| 📁 File Explorer | درخت فایل/پوشه با ساخت / تغییر نام / حذف (منوی کناری از چپ) |
| 💾 Auto Save | ذخیره خودکار ۱.۲ ثانیه بعد از توقف تایپ + دکمه ذخیره دستی |
| ↩️ Undo / Redo | با دکمه در تولبار |
| 🔍 Search & Replace | پنل جستجوی سفارشی موبایل‌پسند |
| 🔢 امکانات ادیتور | شماره خط، هایلایت خط فعلی، Bracket Matching، Auto-indent، Tab واقعی |
| ⌨️ نوار میانبر | `< > / : ; { } ( ) " ' = $ Tab` — قابل اسکرول افقی |
| 🔤 فونت قابل تنظیم | JetBrains Mono + اندازه فونت ۱۰ تا ۳۰ |
| 🌙 تم تاریک | دقیقاً VSCode Dark+ |

## 🏗 معماری

```
Kotlin (UI, FileSystem, Bridge)
        │  JavascriptInterface
        ▼
WebView ← editor/index.html + editor.bundle.js (CodeMirror 6, offline)
        │
        ▼
Live Preview (WebView دوم) ← PreviewBuilder (inline کردن CSS/JS/تصاویر + تزریق Bootstrap CDN)
```

- **بدون هیچ وابستگی خارجی** — فقط Kotlin stdlib + Android SDK
- ذخیره فایل‌ها در حافظه اختصاصی اپ: `filesDir/Projects/<نام‌پروژه>/`
- پروژه جدید به‌صورت پیش‌فرض شامل `index.html` (با لینک Bootstrap CDN)، `style.css` و `script.js`

## 📂 ساختار پروژه

```
CodeStudio/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/codestudio/editor/
│   │   ├── MainActivity.kt      ← UI، تب‌ها، drawer، پیش‌نمایش، منوها
│   │   ├── EditorBridge.kt      ← پل JavaScript ↔ Kotlin
│   │   ├── FileManager.kt       ← مدیریت پروژه‌ها و فایل‌ها
│   │   ├── PreviewBuilder.kt    ← ساخت خروجی Live Preview
│   │   └── Templates.kt         ← قالب‌های آماده پروژه
│   ├── assets/
│   │   ├── editor/              ← هسته CodeMirror 6 (bundle شده با esbuild)
│   │   │   ├── index.html
│   │   │   ├── editor.bundle.js
│   │   │   └── JetBrainsMono-*.woff2
│   │   └── bootstrap.json       ← ۱۷۵۰+ کلاس Bootstrap 5 برای autocomplete
│   └── res/                     ← layout، آیکون‌های Material (vector)، تم تاریک
├── build.gradle  /  settings.gradle   ← قابل باز شدن در Android Studio
└── README.md
```

## 🔨 ساخت (Build)

### Android Studio (پیشنهادی)
پروژه را باز کنید و `Build > Generate Signed APK` بزنید. نیازی به وابستگی اضافه نیست.

### خط فرمان (بدون Gradle)
اسکریپت خط تولید دستی: `aapt2 → ECJ → kotlinc → d8 → zipalign → apksigner`

```bash
# پیش‌نیازها: JDK، android-34 platform، build-tools 34، kotlin-compiler 2.0.20، ecj
keytool -genkeypair -keystore app/codestudio.keystore -alias codestudio \
  -keyalg RSA -keysize 2048 -validity 10950 \
  -storepass codestudio2024 -keypass codestudio2024 \
  -dname "CN=CodeStudio, O=CodeStudio, C=IR"
bash build_apk.sh   # خروجی: CodeStudio-v1.0.0.apk
```

## 📥 نصب

APK نسخه ۱.۰.۰ را از بخش [Releases](../../releases) دانلود کنید و نصب کنید (اجازه «منابع ناشناس» را بدهید).

> **حداقل اندروید:** 7.0 (API 24) — **هدف:** Android 14 (API 34)

## 📝 نکات

- «تزریق CDN بوت‌استرپ» در پنل پیش‌نمایش فقط برای مشاهده است؛ برای پروژه واقعی لینک CDN در HTML خودتان قرار دهید (قالب پیش‌فرض از قبل دارد).
- برای دیدن پیش‌نمایش با کامپوننت‌های تعاملی Bootstrap (مودال، dropdown و ...) اینترنت لازم است.
- Emmet: در فایل خالی HTML تایپ کنید `!` و Enter بزنید.
