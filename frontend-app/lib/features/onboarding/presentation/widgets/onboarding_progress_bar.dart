import 'package:flutter/material.dart';

class OnboardingProgressBar extends StatelessWidget {
  const OnboardingProgressBar({
    super.key,
    required this.currentStep,
    required this.totalSteps,
  });

  final int currentStep;
  final int totalSteps;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: List.generate(totalSteps, (i) {
        final isFilled = i < currentStep;
        return Expanded(
          child: Container(
            margin: EdgeInsets.only(left: i == 0 ? 0 : 4),
            height: 6,
            decoration: BoxDecoration(
              // Track: surface.sunken = neutral-100
              color: const Color(0xFFEEF1F9),
              borderRadius: BorderRadius.circular(9999),
            ),
            child: isFilled
                ? DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: const LinearGradient(
                        begin: Alignment.centerLeft,
                        end: Alignment.centerRight,
                        colors: [Color(0xFF3D5BFF), Color(0xFF7364FF)],
                      ),
                      borderRadius: BorderRadius.circular(9999),
                    ),
                  )
                : null,
          ),
        );
      }),
    );
  }
}
