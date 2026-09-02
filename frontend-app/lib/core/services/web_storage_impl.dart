import 'package:web/web.dart' as web;

/// Web implementation — reads/writes browser localStorage.
String? webStorageRead(String key) => web.window.localStorage.getItem(key);

void webStorageWrite(String key, String value) {
  web.window.localStorage.setItem(key, value);
}

void webStorageRemove(String key) {
  web.window.localStorage.removeItem(key);
}
