package de.fu_berlin.inf.dpp.intellij.filesystem;

import de.fu_berlin.inf.dpp.filesystem.IPath;
import de.fu_berlin.inf.dpp.filesystem.IReferencePoint;

public class IntelliJReferencePointImpl implements IReferencePoint {

  private final IPath path;

  public IntelliJReferencePointImpl(IPath path) {
    this.path = path;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((path == null) ? 0 : path.hashCode());
    return result;
  }

  @Override
  public IPath getPathRepresentation() {
    return this.path;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    IntelliJReferencePointImpl other = (IntelliJReferencePointImpl) obj;
    if (path == null) {
      if (other.path != null) return false;
    } else if (!path.equals(other.path)) return false;
    return true;
  }

  @Override
  public String toString() {
    return path.toString();
  }
}
