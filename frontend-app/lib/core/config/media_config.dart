import 'package:flutter/foundation.dart';

/// Base URL for SeaweedFS content-media assets.
///
/// The backend's `GET /api/feed` returns `media[].s3Url` values as **paths**
/// relative to the S3 bucket root (e.g. `/content-media/telegram/...jpg`).
/// SeaweedFS is not proxied through the api-gateway — clients fetch media
/// directly. This class resolves a relative path to an absolute URL the
/// browser can load.
///
/// Override at build time:
///
/// ```
/// flutter run --dart-define=MEDIA_BASE_URL=http://localhost:28090
/// ```
///
/// Production deployments should set `MEDIA_BASE_URL=https://media.skd.io`
/// (or whatever the CDN host is).
class MediaConfig {
  MediaConfig._();

  static const String _envBaseUrl = String.fromEnvironment(
    'MEDIA_BASE_URL',
    defaultValue: '',
  );

  /// Root URL under which `s3Url` paths resolve.
  /// Web dev default: `http://localhost:28090` — matches the typical
  /// `kubectl port-forward svc/seaweedfs 28090:9000` used in this repo.
  /// Android emulator points at the host's 28090 via `10.0.2.2`.
  static String get baseUrl {
    if (_envBaseUrl.isNotEmpty) return _envBaseUrl;
    if (kIsWeb) return 'http://localhost:28090';
    return 'http://10.0.2.2:28090';
  }

  /// Hostnames + ports that the backend may embed in absolute media URLs
  /// (seaweedfs seen from inside the cluster). On the client the port is
  /// different (kubectl port-forward exposes 28090), so we rewrite the
  /// origin to [baseUrl] while keeping the path.
  static const List<String> _internalMediaHosts = <String>[
    'localhost:19000',
    'seaweedfs:9000',
    'seaweedfs.skd.svc.cluster.local:9000',
    'seaweedfs.skd:9000',
  ];

  /// Resolves [pathOrUrl] to an absolute URL reachable from the client.
  ///
  /// * `null` / empty → `null` (caller should fall back to a placeholder).
  /// * Relative (`/content-media/...` or `content-media/...`) → joined to
  ///   [baseUrl].
  /// * Absolute with an internal S3/SeaweedFS origin (see
  ///   [_internalMediaHosts]) → re-based onto [baseUrl] so the port matches
  ///   the current port-forward.
  /// * Any other absolute URL → returned as-is.
  static String? resolve(String? pathOrUrl) {
    if (pathOrUrl == null || pathOrUrl.isEmpty) return null;

    final String trimmedBase = baseUrl.endsWith('/')
        ? baseUrl.substring(0, baseUrl.length - 1)
        : baseUrl;

    if (pathOrUrl.startsWith('http://') || pathOrUrl.startsWith('https://')) {
      final Uri? parsed = Uri.tryParse(pathOrUrl);
      if (parsed != null) {
        final String hostPort = parsed.hasPort
            ? '${parsed.host}:${parsed.port}'
            : parsed.host;
        if (_internalMediaHosts.contains(hostPort)) {
          final String suffix = parsed.hasQuery
              ? '${parsed.path}?${parsed.query}'
              : parsed.path;
          final String pathPart =
              suffix.startsWith('/') ? suffix : '/$suffix';
          return '$trimmedBase$pathPart';
        }
      }
      return pathOrUrl;
    }

    final String path = pathOrUrl.startsWith('/') ? pathOrUrl : '/$pathOrUrl';
    return '$trimmedBase$path';
  }
}
