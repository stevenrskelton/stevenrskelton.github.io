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

### Microsoft owns Github

Possible to build using App Center (Using AppCenter for Flutter projects)[https://www.rocksolidknowledge.com/articles/using-appcenter-for-flutter-projects] 
by manually manipulating pipeline by explicitly including your gradle-wrapper.jar and inserting using custom POST Git clone steps available on the [App Center Github account](https://github.com/microsoft/appcenter/tree/master/sample-build-scripts/flutter), but issues left unresolved and unsupported. Last update Aug 2020

Clearly in the Readme.md
> Currently, Flutter is not supported by App Center.

