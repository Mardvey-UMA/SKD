import 'package:equatable/equatable.dart';

class CheckoutResult extends Equatable {
  const CheckoutResult({
    required this.paymentId,
    required this.confirmationUrl,
  });

  final String paymentId;
  final String confirmationUrl;

  factory CheckoutResult.fromJson(Map<String, dynamic> json) => CheckoutResult(
    paymentId: (json['payment_id'] ?? json['paymentId']) as String,
    confirmationUrl:
        (json['confirmation_url'] ?? json['confirmationUrl']) as String,
  );

  @override
  List<Object?> get props => [paymentId, confirmationUrl];
}
