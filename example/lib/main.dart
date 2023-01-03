import 'package:flutter/material.dart';
import 'package:workmanager/workmanager.dart';
import 'package:flutter_overlay_window/flutter_overlay_window.dart';

import 'home.dart';
import 'overlay.dart';

void main() {
  // ensure plugins attached/initialized
  WidgetsFlutterBinding.ensureInitialized();
  // initialize workmanager/background task entrypoint
  Workmanager().initialize(callbackDispatcher, isInDebugMode: true);

  runApp(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      // optional: personal prefernce
      theme: ThemeData.light(useMaterial3: true),
      home: Scaffold(
        appBar: AppBar(title: const Text("Flutter Overlay Window Test")),
        body: const HomePage(),
      ),
    ),
  );
}

@pragma("vm:entry-point")
void overlayMain() {
  // 👆 entry point for the overlay window
  // call runApp with MaterialApp
  WidgetsFlutterBinding.ensureInitialized();

  runApp(
    const MaterialApp(
      debugShowCheckedModeBanner: false,
      home: MyOverlay(),
    ),
  );
}

@pragma("vm:entry-point")
void callbackDispatcher() {
  // entry point for workmanager/background task
  Workmanager().executeTask((taskName, inputData) async {
    // you can use match statements in case of multiple tasks
    debugPrint("[ WORKER ]: executing $taskName task");

    final bool hasPermission = await FlutterOverlayWindow.isPermissionGranted();
    final bool isActive = await FlutterOverlayWindow.isActive();

    if (hasPermission && !isActive) {
      debugPrint("[ WORKER ]: showing overlay");

      // show overlay
      await FlutterOverlayWindow.showOverlay(
        enableDrag: false,
        flag: OverlayFlag.clickThrough,
        overlayTitle: "Background Overlay",
        overlayContent: "Showing overlay from background",
        // to show in foreground notification
      );
      // optional: animate in
      FlutterOverlayWindow.shareData("animateIn");

      // maybe do some task...
      await Future.delayed(const Duration(seconds: 6));

      // optional: send data to overlay
      FlutterOverlayWindow.shareData("animateOut");
      await Future.delayed(
        // wait for animation to finsih
        MyOverlay.animationDuration,
      );

      // then close the overlay
      await FlutterOverlayWindow.closeOverlay();

      debugPrint("[ WORKER ]: closed overlay!");
    } else if (!hasPermission) {
      debugPrint("[ WORKER ]: doesn't have permission to show overlay!");
    } else if (isActive) {
      debugPrint("[ WORKER ]: overlay already present!");
    }

    return true;
  });
}
