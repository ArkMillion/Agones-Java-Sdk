package cn.arkmillion.agones.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ModelCollections {
  private ModelCollections() {}

  static <T> List<T> immutableList(List<T> source) {
    return Collections.unmodifiableList(new ArrayList<T>(source));
  }

  static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
    return Collections.unmodifiableMap(new LinkedHashMap<K, V>(source));
  }
}
