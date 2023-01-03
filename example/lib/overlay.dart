import 'package:flutter/material.dart';
import 'package:flutter_overlay_window/flutter_overlay_window.dart';

class MyOverlay extends StatefulWidget {
  const MyOverlay({super.key});

  // optional: transition duration
  static const Duration animationDuration = Duration(milliseconds: 400);

  @override
  State<MyOverlay> createState() => _MyOverlayState();
}

class _MyOverlayState extends State<MyOverlay> {
  bool _show = false;

  _animateIn() => setState(() => _show = true);

  _animateOut() => setState(() => _show = false);

  @override
  void initState() {
    super.initState();
    // listen for events from main app/background
    FlutterOverlayWindow.overlayListener.listen((event) {
      debugPrint("[ MyOverlay ]: recieved event:$event");

      switch (event) {
        case "animateOut":
          _animateOut();
          break;

        case "animateIn":
          WidgetsBinding.instance.addPostFrameCallback(
            (timeStamp) => _animateIn(),
          );
          break;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      type: MaterialType.transparency,
      child: Align(
        alignment: Alignment.topLeft,
        child: AnimatedScale(
          curve: Curves.easeOutBack,
          duration: MyOverlay.animationDuration,
          scale: _show ? 1 : 0,
          child: Container(
            height: 60,
            width: 140,
            margin: const EdgeInsets.all(15),
            decoration: BoxDecoration(
              color: Colors.black,
              borderRadius: BorderRadius.circular(25),
            ),
            alignment: Alignment.center,
            child: const Text(
              "MyOverlay",
              style: TextStyle(fontSize: 20, color: Colors.white),
            ),
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    FlutterOverlayWindow.disposeOverlayListener();
    super.dispose();
  }
}
