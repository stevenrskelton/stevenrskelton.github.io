---
#layout: post
published: false
title: "Github Action to Submit Flutter Android Apps to Play"
categories:
  - Flutter
---

### App Center

Still builds
https://devblogs.microsoft.com/appcenter
with articles such as [Migrating off App Center Push](https://devblogs.microsoft.com/appcenter/migrating-off-app-center-push/)
[Public Preview for App Center Build Export](https://devblogs.microsoft.com/appcenter/public-preview-for-app-center-build-export/), 

App Center Analytics & Diagnostics Data Retention from 90 to 28 days [App Center Analytics & Diagnostics Data Retention](https://devblogs.microsoft.com/appcenter/app-center-analytics-diagnostics-data-retention/)

the focus is to get everyone moved to [Azure pipeline](https://blogs.infosupport.com/flutter-app-center-with-azure-pipelines/)

If you have an Azure account, not only simplified from Microsoft side, but more likely to use other cloud services

Possible to build using App Center (Using AppCenter for Flutter projects)[https://www.rocksolidknowledge.com/articles/using-appcenter-for-flutter-projects] 
by manually manipulating pipeline by explicitly including your gradle-wrapper.jar and inserting using custom POST Git clone steps available on the [App Center Github account](https://github.com/microsoft/appcenter/tree/master/sample-build-scripts/flutter), but issues left unresolved and unsupported. Last update Aug 2020

Clearly in the Readme.md
> Currently, Flutter is not supported by App Center.

Errors https://stackoverflow.com/questions/54962086/gradle-build-fails-due-to-lint-classpath-error-on-android
https://stackoverflow.com/questions/67278979/flutter-appcenter-gradle-plugin-4-0-0-build-issue


### Microsoft owns Github

https://github.com/marketplace/actions/flutter-action
https://github.com/sagar-viradiya/internal-app-sharing-action
https://github.com/r0adkll/sign-android-release

Create Github actions

```yaml
uses: actions/checkout@v3
      - uses: actions/setup-java@v3.1.0
        with:
          distribution: 'zulu'
          java-version: '11'
      - uses: subosito/flutter-action@v2.3.0
        with:
          flutter-version: '2.10.4'
      - run: flutter pub get
#       - run: flutter test
      - run: flutter build apk
      - run: flutter build appbundle
#       - name: Sign Android release
#         uses: r0adkll/sign-android-release@v1
#         with:
#           releaseDirectory: build/app/outputs/bundle/release/
#           signingKeyBase64: "${{ secrets.UPLOAD_SIGNING_KEY_BASE64 }}"
#           alias: "${{ secrets.UPLOAD_SIGNING_KEY_ALIAS }}"
#           keyStorePassword: "${{ secrets.UPLOAD_SIGNING_KEY_STORE_PASSWORD }}"
#           keyPassword: "${{ secrets.UPLOAD_SIGNING_KEY_PASSWORD }}"
      - name: Upload Android Release to Play Store
        uses: r0adkll/upload-google-play@v1.0.16
```
