import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../domain/entities/device_info.dart';
import '../providers/device_info_provider.dart';

/// Screen that displays device information.
///
/// Uses [ConsumerWidget] to watch the [DeviceInfoNotifier] provider
/// and handles all async states (loading, data, error).
class DeviceInfoScreen extends ConsumerWidget {
  const DeviceInfoScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final deviceInfoAsync = ref.watch(deviceInfoNotifierProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Информация об устройстве'),
        centerTitle: true,
      ),
      body: switch (deviceInfoAsync) {
        AsyncData(:final value) => _DeviceInfoContent(deviceInfo: value),
        AsyncLoading() => const Center(child: CircularProgressIndicator()),
        AsyncError(:final error) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline, size: 48, color: Colors.red),
              const SizedBox(height: 16),
              Text(
                'Error: ${error.toString()}',
                style: const TextStyle(color: Colors.red),
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
        _ => const SizedBox.shrink(),
      },
    );
  }
}

/// Widget that displays the device information in a card list.
class _DeviceInfoContent extends StatelessWidget {
  final DeviceInfo deviceInfo;

  const _DeviceInfoContent({required this.deviceInfo});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Card(
        child: Column(
          children: [
            _InfoTile(
              icon: Icons.devices,
              label: 'Имя устройства',
              value: deviceInfo.deviceName,
            ),
            const Divider(height: 1),
            _InfoTile(
              icon: Icons.phone_android,
              label: 'Модель',
              value: deviceInfo.model,
            ),
            const Divider(height: 1),
            _InfoTile(
              icon: Icons.phone_iphone,
              label: 'Платформа',
              value: deviceInfo.platform,
            ),
            const Divider(height: 1),
            _InfoTile(
              icon: Icons.info,
              label: 'Версия ОС',
              value: deviceInfo.osVersion,
            ),
            const Divider(height: 1),
            _InfoTile(
              icon: Icons.code,
              label: 'Версия SDK',
              value: deviceInfo.sdkVersion.toString(),
            ),
            const Divider(height: 1),
            _InfoTile(
              icon: Icons.verified,
              label: 'Версия приложения',
              value: deviceInfo.appVersion,
            ),
          ],
        ),
      ),
    );
  }
}

/// A single info row with icon, label, and value.
class _InfoTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;

  const _InfoTile({
    required this.icon,
    required this.label,
    required this.value,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icon),
      title: Text(label, style: const TextStyle(fontWeight: FontWeight.bold)),
      subtitle: Text(value),
    );
  }
}
