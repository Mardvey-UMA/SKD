import '../../../../core/sources/domain/space_color.dart';
import '../entities/space.dart';
import '../entities/space_feed_page.dart';

abstract interface class ISpacesRepository {
  Future<List<Space>> listMySpaces();
  Future<Space> createSpace({
    required String name,
    required SpaceColor color,
    required List<String> sourceIds,
  });
  Future<Space> updateSpace(
    String id, {
    String? name,
    SpaceColor? color,
    List<String>? sourceIds,
  });
  Future<void> deleteSpace(String id);
  Future<SpaceFeedPage> fetchSpaceFeed(
    String id, {
    String? cursor,
    int limit = 20,
  });
}
