import 'package:flutter/material.dart';
import 'package:flutter_overlay_window/flutter_overlay_window.dart';
import 'package:kt_overlay_window_test/overlay.dart';
import 'package:workmanager/workmanager.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  bool _hasPermission = false;
  bool _isActive = false;

  @override
  void initState() {
    super.initState();
    _initAsyncState();
  }

  _initAsyncState() async {
    final bool hasPermission = await FlutterOverlayWindow.isPermissionGranted();
    final bool isActive = await FlutterOverlayWindow.isActive();

    setState(() {
      _hasPermission = hasPermission;
      _isActive = isActive;
    });
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.maxFinite,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          _hasPermission
              ? Container(
                  padding: const EdgeInsets.symmetric(
                    vertical: 5,
                    horizontal: 15,
                  ),
                  margin: const EdgeInsets.symmetric(vertical: 10),
                  decoration: BoxDecoration(
                    border: Border.all(color: Colors.green.shade400),
                    borderRadius: BorderRadius.circular(15),
                  ),
                  child: const Text(
                    "Permission Granted!",
                    style: TextStyle(
                      fontSize: 16,
                      color: Colors.green,
                    ),
                  ),
                )
              : TextButton(
                  onPressed: _requestPermission,
                  child: const Text("Request Permission"),
                ),
          TextButton(
            onPressed: _hasPermission
                ? _isActive
                    ? _closeOverlay
                    : _showOverlay
                : null,
            child: Text(_isActive ? "Close Overlay" : "Show Overlay"),
          ),
          TextButton(
            onPressed: _hasPermission ? _registerWorker : null,
            child: const Text("Register Worker"),
          ),
        ],
      ),
    );
  }

  _requestPermission() {
    FlutterOverlayWindow.requestPermission().then(
      (bool? gotPermission) {
        if (gotPermission != true) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text("Permission isn't granted!")),
          );
        }
        setState(() => _hasPermission = gotPermission ?? false);
      },
    );
  }

  _showOverlay() async {
    await FlutterOverlayWindow.showOverlay(
      enableDrag: false,
      flag: OverlayFlag.clickThrough,
      overlayTitle: "Overlay Plugin",
      overlayContent: "Showing MyOverlay",
    );
    // optional: animate in
    FlutterOverlayWindow.shareData("animateIn");

    setState(() => _isActive = true);
  }

  _closeOverlay() async {
    // optional: animate overlay out
    FlutterOverlayWindow.shareData("animateOut");
    await Future.delayed(MyOverlay.animationDuration);

    final bool? closed = await FlutterOverlayWindow.closeOverlay();
    setState(() => _isActive = !(closed ?? false));
  }

  _registerWorker() {
    Workmanager().registerOneOffTask(
      "overlay_worker",
      "show_overlay",
      initialDelay: const Duration(seconds: 10),
    );
    debugPrint("[ DEBUG ]: Registered worker!");
  }
}
