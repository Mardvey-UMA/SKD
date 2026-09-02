import 'package:dio/dio.dart';
import '../../../../core/errors/dio_error_handler.dart';
import '../../domain/models/checkout_result.dart';
import '../../domain/models/subscription_status.dart';
import '../../domain/repositories/i_subscription_repository.dart';

class ApiSubscriptionRepository implements ISubscriptionRepository {
  const ApiSubscriptionRepository(this._dio);

  final Dio _dio;

  @override
  Future<SubscriptionStatus> getStatus() async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/api/subscription/status',
      );
      return SubscriptionStatus.fromJson(response.data!);
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<CheckoutResult> checkout(String plan) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/subscription/checkout',
        data: {'plan': plan},
      );
      return CheckoutResult.fromJson(response.data!);
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }

  @override
  Future<void> cancelAutoRenewal() async {
    try {
      await _dio.post<void>('/api/subscription/cancel');
    } on DioException catch (e) {
      throw DioErrorHandler.handle(e);
    }
  }
}
