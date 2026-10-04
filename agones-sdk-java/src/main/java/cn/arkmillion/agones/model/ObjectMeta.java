package cn.arkmillion.agones.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Immutable subset of Kubernetes ObjectMeta exposed by the Agones SDK. */
public final class ObjectMeta {
  private final String name;
  private final String namespace;
  private final String uid;
  private final String resourceVersion;
  private final long generation;
  private final Instant creationTimestamp;
  private final Instant deletionTimestamp;
  private final Map<String, String> annotations;
  private final Map<String, String> labels;

  public ObjectMeta(
      String name,
      String namespace,
      String uid,
      String resourceVersion,
      long generation,
      Instant creationTimestamp,
      Instant deletionTimestamp,
      Map<String, String> annotations,
      Map<String, String> labels) {
    this.name = Objects.requireNonNull(name, "name");
    this.namespace = Objects.requireNonNull(namespace, "namespace");
    this.uid = Objects.requireNonNull(uid, "uid");
    this.resourceVersion = Objects.requireNonNull(resourceVersion, "resourceVersion");
    this.generation = generation;
    this.creationTimestamp = creationTimestamp;
    this.deletionTimestamp = deletionTimestamp;
    this.annotations = ModelCollections.immutableMap(annotations);
    this.labels = ModelCollections.immutableMap(labels);
  }

  public String getName() {
    return name;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getUid() {
    return uid;
  }

  public String getResourceVersion() {
    return resourceVersion;
  }

  public long getGeneration() {
    return generation;
  }

  public Instant getCreationTimestamp() {
    return creationTimestamp;
  }

  public Instant getDeletionTimestamp() {
    return deletionTimestamp;
  }

  public Map<String, String> getAnnotations() {
    return annotations;
  }

  public Map<String, String> getLabels() {
    return labels;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof ObjectMeta)) return false;
    ObjectMeta that = (ObjectMeta) other;
    return generation == that.generation
        && name.equals(that.name)
        && namespace.equals(that.namespace)
        && uid.equals(that.uid)
        && resourceVersion.equals(that.resourceVersion)
        && Objects.equals(creationTimestamp, that.creationTimestamp)
        && Objects.equals(deletionTimestamp, that.deletionTimestamp)
        && annotations.equals(that.annotations)
        && labels.equals(that.labels);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        name,
        namespace,
        uid,
        resourceVersion,
        generation,
        creationTimestamp,
        deletionTimestamp,
        annotations,
        labels);
  }

  @Override
  public String toString() {
    return "ObjectMeta{name='"
        + name
        + "', namespace='"
        + namespace
        + "', generation="
        + generation
        + "}";
  }
}
