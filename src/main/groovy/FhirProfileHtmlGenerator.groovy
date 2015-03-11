/*
        Copyright (C) 2014 The MITRE Corporation. All Rights Reserved.

 The program is provided "as is" without any warranty express or implied, including
 the warranty of non-infringement and the implied warranties of merchantability and
 fitness for a particular purpose.  The Copyright owner will not be liable for any
 damages suffered by you as a result of using the Program.  In no event will the
 Copyright owner be liable for any special, indirect or consequential damages or
 lost profits even if the Copyright owner has been advised of the possibility of
 their occurrence.
*/
import groovy.transform.TypeChecked
import nl.fountain.xelem.excel.Cell
import nl.fountain.xelem.excel.Worksheet

import java.util.regex.Pattern

/**
 * @author Jason Mathews, MITRE Corporation
 * Created on 12/8/2014.
 */
class FhirProfileHtmlGenerator extends FhirProfileScanner {

  /**
   * Flag to skip non-mustSupport elements in HTML documentation
   */
  boolean includeMustSupportOnly = true

  String shortTitle = 'FHIR'
  String overviewTitle = 'CQF FHIR Quality Profiles'

  final List<String> classes = new ArrayList<>()
  final Set<IndexInfo> index = new TreeSet<>()
  final Map<String, Profile> profiles = new HashMap<>()

  final String baseUrl = 'http://hl7-fhir.github.io/'

  FhirProfileHtmlGenerator(Pattern profilePattern) {
    super(profilePattern)
  }

  /**
   * Called at startup before any profiles are processed
   */
  @TypeChecked
  void setup() {
    File docsDir = new File('docs/pages')
    if (!docsDir.exists() && !docsDir.mkdirs()) {
      throw new IOException("failed to create docs folder structure")
    }
  }

  @TypeChecked
  void tearDown() {
    createAllClassesFrame()
    createOverviewSummary()
    copyIndexTemplate()
    createIndexPage()
  }

  /**
   * Process resource profile worksheet
   *
   * @param mapping   Mapping of base resource elements to their definition
   * @param worksheet Active worksheet for this profile
   * @param indexMap  Map of all column names to column index
   */
  void processProfile(Map<String, Details> mapping, Worksheet worksheet,
                    Map<String, Integer> indexMap) {

    // check if profile is disabled
    if (profile.isDisabled()) return

    String id = profile.id
    println id
    classes.add(id)
    profiles.put(id, profile)

    def writer = new FileWriter("docs/pages/${id}.html")
    def html = new groovy.xml.MarkupBuilder(writer)
    html.html {
      head {
        link(rel: 'stylesheet', type: 'text/css', href: '../stylesheet.css', title: 'Style')
      }
      body {

        createDetailNavbar(html, id)

        mkp.yieldUnescaped('\n<!-- ======== START OF CLASS DATA ======== -->')
        div(class:'header') {
          h2(title: "Class $id", class: 'title') {
            String href = String.format('%s%s-%s.html', baseUrl, id, profile.worksheetName, id)
            a(href:href, "Class $id")
          }
        }

        div(class:'contentContainer') {
          ul(class:'inheritance') {
            li{
              mkp.yield('Parent resource:')
              // e.g., resourceName AllergyIntolerance
              // http://hl7-fhir.github.io/allergyintolerance.html
              a(href:"$baseUrl${resourceName.toLowerCase()}.html", resourceName)
            } // li
          } // ul

          // TODO add profile-level description
        if (profile.description)
          div(class:'description') {
            ul(class: 'blockList') {
              li(class: 'blockList') {
                mkp.yield(profile.description)
              }
            }
          }

        div(class:'summary') {
          ul(class: 'blockList') {
            li(class: 'blockList') {
              ul(class: 'blockList') {
                li(class: 'blockList') {
                  mkp.yieldUnescaped('\n<!-- =========== FIELD SUMMARY =========== -->')
                  a(name: 'field_summary') {
                    mkp.yieldUnescaped('<!--   -->')
                  }
                  h3('Field Summary')

                  mkp.yieldUnescaped('''\n<table class="overviewSummary" border="0" cellpadding="3" cellspacing="0"
 summary="Field Summary table, listing fields, and an explanation">
<caption><span>Fields</span><span class="tabEnd">&nbsp;</span></caption>
<tr>
<th class="colFirst" scope="col">Field</th>
<th class="colLast" scope="col">Type and Description</th>
</tr>
''')
                  int count = 0

                  // column index: [??? Mapping:22, Aliases:5, Binding:10, Card.:6, Comments:18, Committee Notes:24, ... ]
                  Integer defIdx = indexMap.get(LABEL_DEFINITION)
                  int eltIdx = indexMap.get(LABEL_ELEMENT)
                  int cardIdx = indexMap.get(LABEL_CARD)
                  int typeIdx = indexMap.get(LABEL_TYPE)
                  int mustIdx = indexMap.get(LABEL_MUST_SUPPORT)

                  //Integer bindIdx = indexMap.get(LABEL_BINDING)
                  // Integer isModIdx = indexMap.get('Is Modifier')
                  // todo Binding, Value, IsModifier, etc.

                  // process structure
                  for (int i = 3; i < 50; i++) {
                    Cell cell1 = worksheet.getCellAt(i, eltIdx)
                    String rawEltName = cell1.getData()
                    String eltName = rawEltName
                    if ('' == eltName) break // element name should not be blank: this means reached the end so stop
                    // if (eltName.endsWith('.extension')) continue // ignore extensions ???
                    if (eltName.startsWith('!')) {
                      continue // ignore ignorable field
                    }
                    Details details = mapping.get(eltName)
                    if (details == null) {
                      if (!rawEltName.startsWith('!')) {
                        details = mapping.get("!" + eltName)
                        if (details != null) warn("profile adding ignorable element: $eltName")
                      } else warn("skip field not in base resource: $eltName")
                      // element not found in base resource
                      continue
                    }
                    String mustSupport = 'Y' == worksheet.getCellAt(i, mustIdx).getData$().trim() ? 'Y' : ''
                    //def s = worksheet.getCellAt(i, mustIdx).getData$()
                    //if (s != '' && s != 'Y' && s != 'N') printf '\tXXX: %s %s%n', eltName, s // bad value
                    if (!mustSupport && includeMustSupportOnly) continue // skip non-mustsupport fields
                    String cardinality = worksheet.getCellAt(i, cardIdx)?.getData() ?: ''

                    // AllergyIntolerance.category -> category
                    // remove base resource name from name
                    String atrName = eltName
                    int ind = atrName.indexOf('.')
                    if (ind > 0) atrName = atrName.substring(ind + 1)

                    if (mustSupport) {
                      String indexName = atrName
                      ind = indexName.lastIndexOf('.')
                      if (ind > 0) indexName = indexName.substring(ind + 1)
                      index.add(new IndexInfo(indexName, id + ".html#$atrName",
                          "Field in class <a href='pages/${id}.html'>$id</a>"))
                    }

                    tr(class:(count++ % 2 == 0 ? 'altColor' : 'rowColor')) {
                      td(class: 'colFirst') {
                        strong {
                          if (mustSupport)
                            a(name: atrName, atrName)
                          else
                            a(name: atrName) {
                              strike(atrName)
                            }
                        }
                      }
                      td(class:'colLast') {
                        if (!mustSupport) mkp.yieldUnescaped('<strike>')
                        String type = worksheet.getCellAt(i, typeIdx).getData$()?.trim() ?: ''
                        if (!type) type = details.type
                        if (type) code(type)
                        if (cardinality) code(cardinality)
                          blockquote {
                            div(class: 'block') {
                              // check if profile overrides the definition
                              String description = details.description
                              if (defIdx) {
                                def val = worksheet.getCellAt(i, defIdx).getData$().trim()
                                if (val) {
                                  description = val
                                  // println "profile override resource element definition: $atrName"
                                }
                              }
                              // TODO Value, Binding, etc.
                              /*
                              if (bindingIdx) {
                                def val = worksheet.getCellAt(i, bindingIdx).getData$().trim()
                                if (val) {
                                  //description += "\nXX: binding: " + val
                                  printf "\t%s binding: %s%n", atrName, val
                                }
                              }
                              if (details.isModifier) {
                                  //description += "\nXX:" + val
                                  printf "\t%s isModifier%n", atrName
                              }
                              */
                              mkp.yield(description)
                            }
                          }
                        if (!mustSupport) mkp.yieldUnescaped('</strike>')
                      } // td
                    } // tr
                  } // for each

                  mkp.yieldUnescaped('</table>')
                } // li
              } // ul
            } // li
          }// ul
        } // div class=summary
        } // div class=contentContainer
      } // body
    } // html
  }

  // ---------------------------------------------------------
  // create allclasses-frame page
  // ---------------------------------------------------------
  void createAllClassesFrame() {
    def writer = new FileWriter('docs/allclasses-frame.html')
    def html = new groovy.xml.MarkupBuilder(writer)

    // sample interface detail page
    // http://docs.oracle.com/javase/7/docs/api/java/awt/Transparency.html

    html.html {
      head {
        base(target: 'right')
        link(rel: 'stylesheet', type: 'text/css', href: 'stylesheet.css', title: 'Style')
      }
      body {
        h1( title: 'FHIR Data Model', class: 'bar') {
          strong('FHIR Data Model')
        }
        div(class: 'indexContainer') {
          ul(title: "Classes") {
            // h1(class: "bar", 'All Classes')
            h2(title: 'Classes', 'Classes')
            classes.each { name ->
              li{
                a(href:"pages/${name}.html", name)
              }
            }
          }
        }
      }
    }
  }


// ---------------------------------------------------------
// create top navpage for detailed pages
// ---------------------------------------------------------
  private void createDetailNavbar(html, String name) {
    // create top navbar HTML for pages/xxx.html
    html.mkp.yieldUnescaped('\n<!-- ========= START OF TOP NAVBAR ======= -->')
    html.div(class:'topNav') {
      a(name:'navbar_top') {
        mkp.yieldUnescaped('\n<!--   -->\n')
      }
      ul(class:'navList', title:'Navigation') {
        li{
          a(href:'../overview-summary.html', 'Overview')
        } // li
        li(class:'navBarCell1Rev','Class')
        li{
          a(href:'../index-files.html', 'Index')
        } // li
      } // ul
      div(class:'aboutLanguage', shortTitle)
    } // div class=topNav

    html.div(class:'subNav') {
      //name = escapeName(name) // e.g. for RIM with <'s in names
      ul(class:'navList') {
        li{
          String nameOut = name
          a(target:'_top', href:"../index.html?pages/${nameOut}.html", 'FRAMES')
          mkp.yieldUnescaped('&nbsp;&nbsp;')
          a(target:'_top', href:"${nameOut}.html", 'NO FRAMES')
        }
      }
      div{
        mkp.yieldUnescaped('<!--   -->')
      }
    } // subNav
    html.mkp.yieldUnescaped('\n<!-- ========= END OF TOP NAVBAR ========= -->')
  } // createDetailNavbar()


// ---------------------------------------------------------
// create overview-summary page
// ---------------------------------------------------------
  void createOverviewSummary() {
    def writer = new FileWriter('docs/overview-summary.html')
    def html = new groovy.xml.MarkupBuilder(writer)
    html.html {
      // see http://docs.oracle.com/javase/7/docs/api/java/lang/package-summary.html
      head {
        //base(target:'left')
        title("Overview (${overviewTitle})")
        link(rel: 'stylesheet', type: 'text/css', href: 'stylesheet.css', title: 'Style')
      }
      body {

        mkp.yieldUnescaped('\n<!-- ========= START OF TOP NAVBAR ======= -->')
        div(class: 'topNav') {
          a(name: 'navbar_top') {
            mkp.yieldUnescaped('\n<!--   -->\n')
          }
          ul(class: 'navList', title: 'Navigation') {
            li(class: 'navBarCell1Rev', 'Overview')
            li('Class')
            li {
              a(href: 'index-files.html', 'Index')
            } // li
          } // ul
          div(class: 'aboutLanguage', shortTitle)
        } // div class=topNav

        div(class: 'subNav') {
          ul(class: 'navList') {
            li {
              a(target: '_top', href: 'index.html?overview-summary.html', 'FRAMES')
              mkp.yieldUnescaped('&nbsp;&nbsp;')
              a(target: '_top', href: 'overview-summary.html', 'NO FRAMES')
            }
          }
          div {
            // script getElementById('allclasses_navbar_top')...
            mkp.yieldUnescaped('<!--   -->')
          }
        } // div class=subNav
        mkp.yieldUnescaped('<!-- ========= END OF TOP NAVBAR ========= -->')

        String description = shortTitle

        div(class: 'header') {
          h1(title: 'Package', class: 'title', overviewTitle)
          div(class: 'docSummary') {
            div(class: 'block', description)
          }
        }

        div(class: 'contentContainer') {
          ul(class: 'blockList') {
            li(class: 'blockList') {
              mkp.yieldUnescaped('''
<table class="packageSummary" border="0" cellpadding="3" cellspacing="0" summary="Class Summary table, listing classes, and an explanation">
<caption><span>Class Summary</span><span class="tabEnd">&nbsp;</span></caption>
<tr>
<th class="colFirst" scope="col">Class</th>
<th class="colLast" scope="col">Description</th>
</tr>
<tbody>''')
              classes.eachWithIndex { String className, idx ->
                String outName = className
                tr(class: idx % 2 ? 'rowColor' : 'altColor') {
                  td(class: 'colFirst') {
                    a(href: "pages/${className}.html", outName)
                  } // td
                  td(class: 'colLast') {
                    def profile = profiles.get(className)
                    if (profile) {
                      def s = profile.description
                      if (s) {
                        div(class: 'block') {
                          mkp.yieldUnescaped(s)
                        } // div
                      }
                    }
                  } //td
                } // tr
              } // each class

              mkp.yieldUnescaped('</tbody></table>')
            } // li
          } // ul
        } // div
      } // body
    } // html
    writer.close()
  } // createOverviewSummary()

// ---------------------------------------------------------

  //@TypeChecked
  void copyIndexTemplate() {
    String body = new File('template/index.html').getText().replaceFirst('%TITLE%', shortTitle)
    new File('docs/index.html').setText(body)
    copyFile('template/stylesheet.css', 'docs/stylesheet.css')
    File dir = new File('docs/resources')
    if (!dir.exists() && dir.mkdir()) {
      copyFile('template/resources/background.gif', 'docs/resources/background.gif')
      copyFile('template/resources/tab.gif', 'docs/resources/tab.gif')
      copyFile('template/resources/titlebar.gif', 'docs/resources/titlebar.gif')
      copyFile('template/resources/titlebar_end.gif', 'docs/resources/titlebar_end.gif')
    }
  }

// ---------------------------------------------------------
// create index page
// ---------------------------------------------------------
  void createIndexPage() {
    def writer = new FileWriter('docs/index-files.html')
    def html = new groovy.xml.MarkupBuilder(writer)

    // sample interface detail page
    // http://docs.oracle.com/javase/7/docs/api/java/awt/Transparency.html

    html.html {
      head{
        title("A-Z Index ($shortTitle)")
        link(rel:'stylesheet', type:'text/css', href:'stylesheet.css', title:'Style')
      }
      body{

        mkp.yieldUnescaped('\n<!-- ========= START OF TOP NAVBAR ======= -->')
        div(class:'topNav') {
          a(name:'navbar_top') {
            mkp.yieldUnescaped('\n<!--   -->\n')
          }
          ul(class:'navList',title:'Navigation') {
            li{
              a(href:'overview-summary.html', 'Overview')
            } // li
            li('Class')
            li(class:"navBarCell1Rev") {
              mkp.yield('Index')
            } // li
          } // ul
          div(class:'aboutLanguage', shortTitle)
        } // div class=topNav

        div(class:'subNav') {
          ul(class:'navList') {
            li{
              a(target:'_top', href:'index.html?index-files.html', 'FRAMES')
              mkp.yieldUnescaped('&nbsp;&nbsp;')
              a(target:'_top', href:'index-files.html', 'NO FRAMES')
            }
          }
          div{
            // script getElementById('allclasses_navbar_top')...
            mkp.yieldUnescaped('<!--   -->')
          }
        } // div class=subNav
        mkp.yieldUnescaped('\n<!-- ========= END OF TOP NAVBAR ========= -->')

        div(class:'contentContainer') {
          final Set<Character> keyset = new HashSet<>()
          index.each{
            keyset.add(Character.toUpperCase(it.name.charAt(0)))
          }

          // A-Z short-cut at top
          for(Character c in 'A'..'Z') {
            if (c != 'A')
              mkp.yieldUnescaped('&nbsp;')
            if (keyset.contains(c))
              a(href:"#$c", c)
            else
              mkp.yield(c)
          }

          char lastRef = ' '
          index.each{
            String key = it.name
            String target = it.href
            //p(class:"strong") {
            char ref = Character.toUpperCase(key.charAt(0))
            if (ref != lastRef) {
              h2(class:'title') {
                a(name:ref, ref)
              }
              lastRef = ref
            }
            a(class:'strong', href:"pages/$target", key)
            if (it.desc) {
              mkp.yieldUnescaped(" - ${it.desc}")
            }
            br()
            //} // span
          } // each

          br()
          p()

          // A-Z short-cut at bottom
          for(Character c in 'A'..'Z') {
            if (c != 'A')
              mkp.yieldUnescaped('&nbsp;')
            if (keyset.contains(c))
              a(href:"#$c", c)
            else
              mkp.yield(c)
          }
        } // div class=contentContainer

        mkp.yieldUnescaped('\n<!-- ======= START OF BOTTOM NAVBAR ====== -->')
        div(class:'bottomNav') {
          a(name:'navbar_bottom') {
            mkp.yieldUnescaped('\n<!--   -->\n')
          }
          ul(class:'navList', title:'Navigation') {
            li{
              a(href:'overview-summary.html', 'Overview')
            } // li
            li('Class')
            li(class:'navBarCell1Rev') {
              mkp.yield('Index')
            } // li
          } // ul
          div(class:'aboutLanguage', shortTitle)
        } // div class=topNav

        div(class:'subNav') {
          ul(class:'navList') {
            li{
              a(target:'_top', href:'index.html?index-files.html', 'FRAMES')
              mkp.yieldUnescaped('&nbsp;&nbsp;')
              a(target:'_top', href:'index-files.html', 'NO FRAMES')
            }
          }
          div{
            // script getElementById('allclasses_navbar_top')...
            mkp.yieldUnescaped('<!--   -->')
          }
        } // div class=subNav
        mkp.yieldUnescaped('<!-- ======== END OF BOTTOM NAVBAR ======= -->')
        //mkp.yieldUnescaped('<dd>&nbsp;</dd>')
      }//body
    }//html
    writer.close()
  } // end createIndexPage

  // -----------------------------------------------

  // utility methods
  @TypeChecked
  static void copyFile(String sourceName, String targetName) {
    File target = new File(targetName)
    if (target.exists()) return
    File source = new File(sourceName)
    if (!source.exists()) throw new FileNotFoundException(source.toString())
    target.setBytes(source.getBytes())
    target.setLastModified(source.lastModified())
  }

  @TypeChecked
  static String escapeName(String s) {
    // invalid characters for windows filenames see http://support.microsoft.com/kb/177506
    s.replaceAll('[:<>?*/|]', '_')
  }

} // FhirProfileHtmlGenerator
