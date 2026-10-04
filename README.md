# LocalMind 0.1

Android üzerinde yerel GGUF model çalıştıran ve çalışan `runtime` kodunu değiştirebilen kişisel AI prototipi.

## Çalışan özellikler
- GGUF model seçme ve uygulamanın özel depolamasına kopyalama
- llama.cpp tabanlı on-device inference (`llama-android` AAR)
- Basit yerel hafıza API'si
- Çalışan arayüz kodunu (`index.html`, `app.js`, `style.css`) uygulama içinden okuma/yazma
- Değişiklik öncesi otomatik snapshot
- Rollback
- `applySelfUpdate(files)` ile runtime koduna toplu patch uygulama

## Bilinçli sınır
Android APK içindeki native Kotlin kodu çalışma anında doğrudan değiştirilemez. Bu prototipte değiştirilebilir uygulama katmanı `filesDir/runtime` içindedir ve WebView tarafından doğrudan çalıştırılır. Native çekirdeği değiştiren özellikler yeni APK derlemesi gerektirir.

## Derleme
Android Studio ile projeyi açın ve `app` modülünü build edin. Proje arm64-v8a GGUF inference için Maven Central'daki `dev.ffmpegkit-maintained:llama-android:0.1.1` bağımlılığını kullanır.

## Model
İlk açılışta `GGUF MODEL SEÇ` düğmesine basın ve telefondaki arm64 uyumlu GGUF modelini seçin. Küçük quantized modeller telefon için daha uygundur.

## Self-update protokolü
WebView tarafında:

```js
applySelfUpdate([
  {name:'style.css', content:'...tam yeni içerik...'},
  {name:'app.js', content:'...tam yeni içerik...'}
])
```

Native katman dosya adlarını allowlist ile sınırlar ve her yazmadan önce snapshot alır.

## Otomatik APK build
Projede `.github/workflows/build-apk.yml` bulunur. Repo GitHub'a koyulduğunda Actions üzerinden debug APK otomatik üretilebilir.
