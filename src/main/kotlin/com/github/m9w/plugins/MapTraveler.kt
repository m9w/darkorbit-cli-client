package com.github.m9w.plugins

import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.metaplugins.MapModule
import com.github.m9w.metaplugins.game.entities.JumpgateImpl

import com.darkorbit.ShipInitializationCommand
import com.github.m9w.Scheduler
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.context

private fun log(message: String) {
    println(message)
}

class MapTraveler() {

    private val entities: EntitiesModule by context
    private val map: MapModule by context
    private val scheduler: Scheduler by context

    private var currentMapName: String = "Unknown"

    private var travelPath: List<String> = emptyList()
    private var traveling = false

    @OnPackage
    private fun onShipInit(init: ShipInitializationCommand) {
        currentMapName = map.findMap(init.mapId).name
    }

    fun startTravel(destinationMap: String) {
        val path = findPath(currentMapName, destinationMap)
        if (path == null) {
            log("MapTraveler: No path found from $currentMapName to $destinationMap")
            return
        }
        log("MapTraveler: Starting travel to $destinationMap. Path: ${path.joinToString(" -> ")}")
        travelPath = path
        traveling = true
        executeNextStep()
    }

    fun stopTravel() {
        log("MapTraveler: Stopping travel.")
        traveling = false
    }

    private fun executeNextStep() {
        if (!traveling || travelPath.isEmpty()) {
            log("MapTraveler: Travel finished or stopped.")
            stopTravel()
            return
        }

        val nextMapName = travelPath.first()
        log("MapTraveler: Current map: $currentMapName, Next map in path: $nextMapName")
        if (currentMapName == nextMapName) {
            log("MapTraveler: Already on map $nextMapName, moving to next step.")
            travelPath = travelPath.drop(1)
            executeNextStep()
            return
        }

        val hero = entities.hero
        val gate = findGateTo(nextMapName)
        if (gate != null) {
            log("MapTraveler: Found gate to $nextMapName: ${gate.id} at ${gate.position}")
            log("MapTraveler: Moving hero to gate position: ${gate.position}")
            hero.moveTo(gate.position) {
                log("MapTraveler: Hero reached gate. Invoking gate.")
                scheduler.schedule(2000){gate.invoke()} // Wait 2 sec to jump Request
                // Wait for map change
                scheduler.schedule(10000) { // Wait for 10 seconds for map change
                    if (map.map.name == nextMapName) {
                        log("MapTraveler: Successfully changed map to $nextMapName. Continuing travel.")
                        executeNextStep()
                    } else {
                        log("MapTraveler: Map change failed or not yet occurred. Retrying or stopping.")
                        // Map change failed or not yet occurred, retry or stop
                        stopTravel()
                    }
                }
            }
        } else {
            log("MapTraveler: No gate found to $nextMapName. Stopping travel.")
            stopTravel()
        }
    }

    private fun findGateTo(targetMapName: String): JumpgateImpl? {
        return entities.values.filterIsInstance<JumpgateImpl>().find { gate ->
            // Placeholder: In a real scenario, you'd have a mapping of gate IDs to destination map IDs.
            // For now, we'll assume a simple heuristic or a hardcoded mapping for testing.
            log("MapTraveler: Considering gate ${gate.id} at ${gate.position}")
            val destinationMapId = getMapIdForGateId(gate.id.toInt())
            if (destinationMapId == null) {
                log("MapTraveler: Gate ${gate.id}: Could not find destination map ID.")
                return@find false
            }
            val foundMap = map.findMap(destinationMapId)
            log("MapTraveler: Gate ${gate.id}: Resolved destination map ID $destinationMapId to map name ${foundMap.name}")
            if (foundMap != null) {
                foundMap.name.toString() == targetMapName.toString()
            } else {
                log("MapTraveler: Gate ${gate.id}: No map found for ID $destinationMapId.")
                false
            }
        }
    }

    private val portIdsMapping = listOf(
        //1-1 DONE
        2 to 150000168,

        //1-2 DONE
        1 to 150000169, // 1-1
        3 to 150000170, // 1-3
        4 to 150000172, // 1-4

        //1-3 DONE
        2 to 150000171, // 1-2
        7 to 150000174, // 2-3
        4 to 150000194, // 1-4
        200 to 150000450, // LOW

        //1-4 DONE
        2 to 150000173, //1-2
        3 to 150000195, //1-3
        13 to 150000198, //4-1
        12 to 150000178, // 3-4

        //1-5 DONE
        16 to 150000317,//4-4
        18 to 150000318,//1-6
        19 to 150000320,//1-7
        29 to 150000346,//4-5

        //1-6 DONE
        17 to 150000319,//1-5
        20 to 150000322,//1-8

        //1-7 DONE
        17 to 150000321,//1-5
        20 to 150000324, //1-8
        0 to 150000502, //QZ

        //1-8 DONE
        18 to 150000323, //1-6
        19 to 150000325,//1-7
        306 to 150000210, //BL Map ID missing


        //2-1 DONE
        6 to 150000183,//2-2

        //2-2 Done
        5 to 150000182,//2-1
        8 to 150000184,//2-4
        7 to 150000177,//2-3

        //2-3 DONE
        6 to 150000176,//2-2
        8 to 150000192,//2-4
        3 to 150000175, // 1-3
        200 to 150000451, // LoW

        //2-4 DONE
        6 to 150000185,// 2-2
        7 to 150000193, // 2-3
        14 to 150000200, // 4-2
        11 to 150000186, // 3-3

        //2-5 DONE
        22 to 150000328,//2-6
        23 to 150000330,//2-7
        29 to 150000348,//4-5
        16 to 150000327,// 4-4

        //2-6 DONE
        21 to 150000329, //2-5
        24 to 150000332, //2-8

        //2-7 DONE
        21 to 150000331,//2-5
        24 to 150000334,//2-8

        //2-8 DONE
        22 to 150000335,//2-7
        307 to 150000214,//BL
        23 to 150000333,//2-6

        //3-1 DONE
        10 to 150000191,

        //3-2 DONE
        9 to 150000190, // 3-1
        12 to 150000181, //3-4
        11 to 150000189, // 3-3

        //3-3 DONE
        10 to 150000188,// 3-2
        12 to 150000197, //3-4
        8 to 150000187, // 2-4
        200 to 150000452, //Low

        //3-4 DONE
        10 to 150000180,// 3-2
        4 to 150000179, //1-4
        15 to 150000202,//4-3
        11 to 150000196,//3-3

        //3-5 DONE
        16 to 150000337, // 4-4
        26 to 150000338, //3-6
        27 to 150000340, //3-7
        29 to 150000350, //4-5

        //3-6 DONE
        25 to 150000341, //3-5
        28 to 150000344, //3-8

        //3-7 DONE
        25 to 150000339, //3-5
        28 to 150000342, //3-8

        //3-8 DONE
        27 to 150000343, //3-6
        26 to 150000345, //3-7
        308 to 150000218, //BL Map

        //4-1 DONE
        4 to 150000199,//1-4
        14 to 150000204,//4-2
        15 to 150000206,//4-3
        16 to 150000307,// 4-4

        //4-2 DONE
        13 to 150000205,//4-1
        8 to 150000201, //2-4
        16 to 150000309, // 4-4
        15 to 150000206, //4-3

        //4-3 DONE
        14 to 150000207, //4-2
        13 to 150000208,//4-1
        12 to 150000203,//3-4
        16 to 150000311,// 4-4

        //4-4 DONE
        13 to 150000308,//4-1
        14 to 150000310,//4-2
        15 to 150000312,//4-3
        17 to 150000316,//1-5
        21 to 150000326,//2-5
        25 to 150000336,//3-5

        //4-5 DONE
        17 to 150000347,//1-5
        91 to 150000455,//5-1
        21 to 150000349,//2-5
        91 to 150000456,//5-1
        25 to 150000351,//3-5
        91 to 150000458,//5-1

        //5-1 DONE
        92 to 150000460,//5-2
        92 to 150000462,//5-2
        92 to 150000464,//5-2

        //5-2 DONE
        93 to 150000470,//5-3
        93 to 150000468,//5-3
        93 to 150000466,//5-3

        //5-3 DONE
        16 to 150000478,//4-4
        16 to 150000480,//4-4
        16 to 150000482,//4-3

        //1-BL ALL ,BL M,AP IDS ARE MISSING!!
        0 to 150000209,
        0 to 150000210,
        20 to 150000208,

        //2-BL
        0 to 150000213,
        0 to 150000214,
        24 to 150000212,

        //3-BL
        28 to 150000216,
        0 to 150000218,
        0 to 150000217
    )

    private fun getMapIdForGateId(gateId: Int): Int? {
        val nextMapID = portIdsMapping.find { it.second == gateId }?.first
        if (nextMapID == null) {
            log("MapTraveler: Unknown port id $gateId")
        }
        return nextMapID
    }

    private fun findPath(from: String, to: String): List<String>? {
        log("MapTraveler: Finding path from $from to $to")
        // This is a simplified map graph for demonstration. In a real scenario, this would be loaded from data.
        val mapGraph = mapOf(
            "1-1" to listOf("1-2"), //DONE
            "1-2" to listOf("1-1", "1-3", "1-4"),//DONE
            "1-3" to listOf("1-2", "1-4", "2-3"),//DONE
            "1-4" to listOf("1-2","1-3", "3-4", "4-1"),//DONE
            "1-5" to listOf("1-6", "1-7", "4-4", "4-5"),//DONE
            "1-6" to listOf("1-5", "1-8"),//DONE
            "1-7" to listOf("1-5", "1-8"),//DONE
            "1-8" to listOf("1-6", "1-7"),//DONE

            "2-1" to listOf("2-2"),
            "2-2" to listOf("2-1", "2-3", "2-4"),
            "2-3" to listOf("2-2", "2-4", "1-3"),
            "2-4" to listOf("2-2","2-3", "3-3", "4-2"),
            "2-5" to listOf("2-6", "2-7", "4-4", "4-5"),
            "2-6" to listOf("2-5", "2-8"),
            "2-7" to listOf("2-5", "2-8"),
            "2-8" to listOf("2-6", "2-7"),

            "3-1" to listOf("3-2"),
            "3-2" to listOf("3-1", "3-3", "3-4"),
            "3-3" to listOf("3-2", "3-3", "3-4", "2-4"),
            "3-4" to listOf("3-2", "3-3", "1-4", "4-3"),
            "3-5" to listOf("3-6", "3-7", "4-4", "4-5"),
            "3-6" to listOf("3-5", "3-8"),
            "3-7" to listOf("3-5", "3-8"),
            "3-8" to listOf("3-6", "3-7"),

            "4-1" to listOf("1-4","4-2","4-3","4-4"),
            "4-2" to listOf("2-4","4-1","4-3","4-4"),
            "4-3" to listOf("3-4","4-1", "4-2", "4-4"),
            "4-4" to listOf("1-5", "2-5", "3-5","4-1","4-2", "4-3"),
            "4-5" to listOf("5-1", "1-5", "2-5", "3-5"),
            "5-1" to listOf("5-2"),
            "5-2" to listOf("5-3"),
            "5-3" to listOf("4-4")
        )

        if (from == to) {
            log("MapTraveler: Already on destination map $to.")
            return listOf(to)
        }

        val queue = ArrayDeque<List<String>>()
        queue.add(listOf(from))
        val visited = mutableSetOf<String>()
        visited.add(from)

        while (queue.isNotEmpty()) {
            val currentPath = queue.removeFirst()
            val currentMap = currentPath.last()

            if (currentMap == to) {
                log("MapTraveler: Path found: ${currentPath.joinToString(" -> ")}")
                return currentPath
            }

            mapGraph[currentMap]?.forEach { neighbor ->
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    queue.add(currentPath + neighbor)
                }
            }
        }
        log("MapTraveler: No path found from $from to $to.")
        return null
    }
    
}
