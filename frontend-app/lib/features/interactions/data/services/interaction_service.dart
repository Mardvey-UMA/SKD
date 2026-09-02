import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/widgets.dart';

import '../../domain/models/interaction_event.dart';

/// Batches interaction events and sends them to POST /api/interactions/batch.
///
/// Flush triggers:
/// - Every 30 seconds (timer-based)
/// - When queue reaches 50 events
/// - When app goes to background / becomes inactive
///
/// On network error: events remain in queue and are retried on next flush.
class InteractionService with WidgetsBindingObserver {
  InteractionService(this._dio);

  final Dio _dio;
  final List<InteractionEvent> _eventQueue = [];
  Timer? _batchTimer;

  static const _batchInterval = Duration(seconds: 30);
  static const _maxBatchSize = 50;

  void init() {
    WidgetsBinding.instance.addObserver(this);
    _startBatchTimer();
  }

  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _batchTimer?.cancel();
    _batchTimer = null;
    // Best-effort flush — fire and forget on dispose
    _flushEvents();
  }

  void trackEvent(InteractionEvent event) {
    _eventQueue.add(event);
    if (_eventQueue.length >= _maxBatchSize) {
      _batchTimer?.cancel();
      _flushEvents();
      _startBatchTimer();
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.inactive ||
        state == AppLifecycleState.detached) {
      _flushEvents();
    }
  }

  void _startBatchTimer() {
    _batchTimer?.cancel();
    _batchTimer = Timer.periodic(_batchInterval, (_) => _flushEvents());
  }

  Future<void> _flushEvents() async {
    if (_eventQueue.isEmpty) return;

    // Drain queue into a local snapshot so new events can queue up.
    final batch = List<InteractionEvent>.from(_eventQueue);
    _eventQueue.clear();

    // API allows max 100 events per batch — split if needed.
    const maxPerRequest = 100;
    final chunks = <List<InteractionEvent>>[];
    for (var i = 0; i < batch.length; i += maxPerRequest) {
      chunks.add(
        batch.sublist(
          i,
          i + maxPerRequest > batch.length ? batch.length : i + maxPerRequest,
        ),
      );
    }

    for (final chunk in chunks) {
      try {
        await _dio.post<Map<String, dynamic>>(
          '/api/interactions/batch',
          data: {'events': chunk.map((e) => e.toJson()).toList()},
        );
      } catch (_) {
        // On error — put events back so they are retried next cycle.
        _eventQueue.insertAll(0, chunk);
      }
    }
  }
}
