#Credits
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

#Apache Isis colors
In the icon: green, orange, red, blue
Violet #8064A2
DarkBlue #4F81BD
LightBlue #4BACC6
Green #9BBB59
Orange #F79646
Red #C0504D

# Components Colors
"JS Runtime"   #f7df1e 
KotlinJS [[https://github.com/JetBrains/kotlin/tree/master/js]] #8167FF-02AEFF {
kotlinx.serialization as KS #DDE1F9/A9B4EF
kvision [[https://rjaros.github.io/kvision]] #00AAD4/216778 
Bootstrap [[https://getbootstrap.com/]] #8F5ADC-31135B
"Font\nAwesome" [[https://fontawesome.com/]] #155592-1C7ED6
Tabulator [[http://tabulator.info/]] #21441F|3DB549
Chart.js [[https://www.chartjs.org/]] #f27173
kroViz #8064A2-F79646

# Convert PNG to SVG
https://www.pngtosvg.com/
