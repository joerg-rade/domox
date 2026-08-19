# Credits
Artwork reused:

* https://commons.wikimedia.org/wiki/File:Vincent_Van_Gogh_-_Wheatfield_with_Crows.jpg by Vincent van Gogh 1890
* https://fr.wikipedia.org/wiki/Fichier:Meuble_corbeau.svg by Henry Salomé le 06/12/2006

Create banner image:
```
convert.exe WheatFieldWithCrows.jpg -channel all isis_clut.png -clut WheatFieldWithCrows.png
```

```
convert.exe WheatFieldWithCrows.jpg -separate -normalize -combine isis_clut.png -clut WheatFieldWithCrows.png
```

# Apache Isis colors

In the icon: green, orange, red, blue

| Name      | Code    | Color                                                     |
|:----------|:--------|:----------------------------------------------------------|
| Violet    | #8064A2 | <span style="color:#8064A2;">&#9608;&#9608;&#9608;</span> |
| DarkBlue  | #4F81BD | <span style="color:#4F81BD;">&#9608;&#9608;&#9608;</span> |
| LightBlue | #4BACC6 | <span style="color:#4BACC6;">&#9608;&#9608;&#9608;</span> |
| Green     | #9BBB59 | <span style="color:#9BBB59;">&#9608;&#9608;&#9608;</span> |
| Orange    | #F79646 | <span style="color:#F79646;">&#9608;&#9608;&#9608;</span> |
| Red       | #C0504D | <span style="color:#C0504D;">&#9608;&#9608;&#9608;</span> |

# Components Colors
| Name / Technology          | Link                                               | Code(s)        | Color.1                                     | Color.2                                     |
|:---------------------------|:---------------------------------------------------|:---------------|:--------------------------------------------|:--------------------------------------------|
| JS Runtime                 | —                                                  | #f7df1e        | <span style="color:#f7df1e;">&#9608;</span> |
| KotlinJS                   | https://github.com/JetBrains/kotlin/tree/master/js | #8167FF-02AEFF | <span style="color:#8167FF;">&#9608;</span> | <span style="color:#02AEFF;">&#9608;</span> |
| kotlinx.serialization (KS) | —                                                  | #DDE1F9-A9B4EF | <span style="color:#DDE1F9;">&#9608;</span> | <span style="color:#A9B4EF;">&#9608;</span> |
| kvision                    | https://rjaros.github.io/kvision                   | #00AAD4-216778 | <span style="color:#00AAD4;">&#9608;</span> | <span style="color:#216778;">&#9608;</span> |
| Bootstrap                  | https://getbootstrap.com/                          | #8F5ADC-31135B | <span style="color:#8F5ADC;">&#9608;</span> | <span style="color:#31135B;">&#9608;</span> |
| Font Awesome               | https://fontawesome.com/                           | #155592-1C7ED6 | <span style="color:#155592;">&#9608;</span> | <span style="color:#1C7ED6;">&#9608;</span> |
| Tabulator                  | http://tabulator.info/                             | #21441F-3DB549 | <span style="color:#21441F;">&#9608;</span> | <span style="color:#3DB549;">&#9608;</span> |
| Chart.js                   | https://www.chartjs.org/                           | #f27173        | <span style="color:#f27173;">&#9608;</span> |                                             |
| kroViz                     | —                                                  | #8064A2-F79646 | <span style="color:#8064A2;">&#9608;</span> | <span style="color:#F79646;">&#9608;</span> |

# Convert PNG to SVG
https://www.pngtosvg.com/

# Convert Image to ASCII Art
https://www.asciiart.eu/image-to-ascii
