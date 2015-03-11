import groovy.transform.TypeChecked

/**
 * Created by MATHEWS on 3/4/2015.
 */
@TypeChecked
class IndexInfo implements Comparable<IndexInfo> {

  final String name
  final String href
  final String desc

  IndexInfo(name, href, desc) {
    if (!name) throw new IllegalArgumentException()
    this.name = name
    if (!href) throw new IllegalArgumentException()
    this.href = href
    if (desc == null) desc = ''
    this.desc = desc
  }

  int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + name.hashCode();
    result = prime * result + desc.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    IndexInfo other = (IndexInfo) obj;
    if (!name.equals(other.name))
      return false;
    return desc.equals(other.desc)
  }

  /**
   * Compare one IndexInfo to another. First compare the
   * names and if names are equal then compare the descriptions.
   * @param   o the IndexInfo to be compared.
   */
  @Override
  int compareTo(IndexInfo o) {
    int cmp = name.compareToIgnoreCase(o.name)
    return cmp != 0 ? cmp : desc.compareToIgnoreCase(o.desc)
  }

}
