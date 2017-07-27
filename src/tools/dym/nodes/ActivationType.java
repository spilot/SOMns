package tools.dym.nodes;

import som.interpreter.objectstorage.ClassFactory;

/**
 * Created by fred on 27/07/17.
 */
public final class ActivationType {
  private final Class<?> javaType;
  private final ClassFactory somType;

  public ActivationType(Class<?> javaType, ClassFactory somType) {
    this.javaType = javaType;
    this.somType = somType;
  }

  public Class<?> getJavaType() {
    return javaType;
  }

  public ClassFactory getSomType() {
    return somType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ActivationType that = (ActivationType) o;

    if (javaType != null ? !javaType.equals(that.javaType) : that.javaType != null) return false;
    return somType != null ? somType.equals(that.somType) : that.somType == null;

  }

  @Override
  public int hashCode() {
    int result = javaType != null ? javaType.hashCode() : 0;
    result = 31 * result + (somType != null ? somType.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "ActType[" + getJavaType() + ", " + getSomType() + "]";
  }
}
