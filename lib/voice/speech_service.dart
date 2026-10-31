import 'package:speech_to_text/speech_to_text.dart' as stt;

/// Thin wrapper around the phone's speech recognition so the screen can be
/// tested with a fake. Audio is handled by Android's own speech service
/// (usually Google's, which may work online); this app stores no audio.
abstract class SpeechService {
  /// Asks for the microphone permission if needed. False = not available.
  Future<bool> initialize(void Function(String message) onError);

  Future<void> start({
    required String localeId,
    required void Function(String text) onText,
    required void Function() onDone,
  });

  Future<void> stop();
}

class DeviceSpeechService implements SpeechService {
  stt.SpeechToText? _speech;
  void Function()? _onDone;

  @override
  Future<bool> initialize(void Function(String message) onError) async {
    try {
      _speech ??= stt.SpeechToText();
      return await _speech!.initialize(
        onError: (e) => onError(e.errorMsg),
        onStatus: (status) {
          if (status == 'done' || status == 'notListening') {
            _onDone?.call();
          }
        },
      );
    } catch (e) {
      onError('$e');
      return false;
    }
  }

  @override
  Future<void> start({
    required String localeId,
    required void Function(String text) onText,
    required void Function() onDone,
  }) async {
    _onDone = onDone;
    await _speech!.listen(
      onResult: (r) => onText(r.recognizedWords),
      localeId: localeId,
    );
  }

  @override
  Future<void> stop() async {
    try {
      await _speech?.stop();
    } catch (_) {
      // nothing to stop
    }
  }
}
