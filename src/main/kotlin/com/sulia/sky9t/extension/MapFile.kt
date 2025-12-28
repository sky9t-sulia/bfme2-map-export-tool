package com.sulia.sky9t.extension

import de.darkatra.bfme2.map.MapFile

fun MapFile.width(): UInt = this.heightMap.width
fun MapFile.height(): UInt = this.heightMap.height