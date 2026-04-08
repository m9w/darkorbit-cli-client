package com.github.m9w.game.entities


@Suppress("unused", "EnumEntryName", "SpellCheckingInspection", "RemoveRedundantBackticks")
enum class GameMapEnum(mapId: Int, exitType: Int? = null) {
    `1-1`(1),  `1-2`(2),  `1-3`(3),  `1-4`(4),
    `2-1`(5),  `2-2`(6),  `2-3`(7),  `2-4`(8),
    `3-1`(9),  `3-2`(10), `3-3`(11), `3-4`(12),

    `4-1`(13), `4-2`(14), `4-3`(15), `4-4`(16),

    `1-5`(17), `1-6`(28), `1-7`(19), `1-8`(20),
    `2-5`(21), `2-6`(22), `2-7`(23), `2-8`(24),
    `3-5`(25), `3-6`(26), `3-7`(27), `3-8`(28),

    `4-5`(29),

    `???`(42),

    `GG Alpha`(51, 1), `GG Betta`(52, 1), `GG Gamma`(53, 1),

    `GG New Client`(54),
    `GG Delta`(55, 1),
    `GG Orb`(56),
    `GG Year6`(57),
    `GG High Score Gate`(58),

    `MMO Invasion 1`(61), `EIC Invasion 1`(62), `VRU Invasion 1`(63),
    `MMO Invasion 2`(64), `EIC Invasion 2`(65), `VRU Invasion 2`(66),
    `MMO Invasion 3`(67), `EIC Invasion 3`(68), `VRU Invasion 3`(69),

    `GG Epsilon`(70, 1),
    `GG Zetta 1`(71, 1), `GG Zetta 2`(72, 1), `GG Zetta 3`(73, 1),
    `GG Kappa`(74),
    `GG Lambda`(75),
    `GG Kronos`(76),
    `GG Cold Wave Easy`(77), `GG Cold Wave Hard`(78),

    `TDM I`(81), `TDM II`(82),

    `5-1`(91), `5-2`(92), `5-3`(93), `5-4`(94),

    `JackPot 1`(101),
    `JackPot 2`(102),
    `JackPot 3`(103),
    `JackPot 4`(104),
    `JackPot 5`(105),
    `JackPot 6`(106),
    `JackPot 7`(107),
    `JackPot 8`(108),
    `JackPot 9`(109),
    `JackPot 10`(110),
    `JackPot 11`(111),

    `Ultimate Battle Arena 1`(112),
    `Ultimate Battle Arena 2`(113),
    `Ultimate Battle Arena 3`(114),
    `Ultimate Battle Arena 4`(115),
    `Ultimate Battle Arena 5`(116),
    `Ultimate Battle Arena 6`(117),
    `Ultimate Battle Arena 7`(118),
    `Ultimate Battle Arena 8`(119),
    `Ultimate Battle Arena 9`(120),
    `Ultimate Battle Arena 10`(121),

    `R-Zone 1`(150),
    `R-Zone 2`(151),
    `R-Zone 3`(152),
    `R-Zone 4`(153),
    `R-Zone 5`(154),
    `R-Zone 6`(155),
    `R-Zone 7`(156),
    `R-Zone 8`(157),
    `R-Zone 9`(158),
    `R-Zone 10`(150),

    `LoW`(200),
    `Sector Control 1`(201), `Sector Control 2`(202),
    `GG Hades`(203, 1),
    `Devolarium Attack`(223),
    `Custom Tournament`(224),

    `GG PET Attack Easy`(225), `GG PET Attack Hard`(226),

    `GG VoT 1`(227),

    `Permafrost Fissure`(228),
    `Quarantine Zone`(229),

    `GG VoT 2`(230),
    `GG VoT 3`(231),
    `GG VoT 4`(232),
    `GG VoT 5`(233),
    `GG VoT 6`(234),
    `GG VoT 7`(235),
    `GG VoT 8`(236),

    `Sigma 1`(300),
    `Sigma 2`(301),
    `Sigma 3`(302),
    `Sigma 4`(303),
    `Sigma 5`(304),

    `Compromising Invasion`(305),

    `1BL`(306),
    `2BL`(307),
    `3BL`(308),

    `Experiment Zone 1`(401),
    `Experiment Zone 2-1`(402),
    `Experiment Zone 2-2`(403),
    `Experiment Zone 2-3`(404),

    `GoP Normal 1`(410),
    `GoP Normal 2`(411),
    `GoP Normal 3`(412),
    `GoP Normal 4`(413),
    `GoP Normal 5`(414),
    `GoP Normal Final`(415, 1),

    `WarGame 1`(420),
    `WarGame 2`(421),
    `WarGame 3`(422),
    `WarGame 4`(423),
    `WarGame 5`(424),
    `WarGame 6`(425),

    `ATLAS A [1]`(430, 55),   `ATLAS A [2]`(430, 55),   `ATLAS A [3]`(430, 55),   `ATLAS A [4]`(430, 55),
    `ATLAS B [1]`(431, 55),   `ATLAS B [2]`(431, 55),   `ATLAS B [3]`(431, 55),   `ATLAS B [4]`(431, 55),
    `ATLAS C [1]`(432, 55),   `ATLAS C [2]`(432, 55),   `ATLAS C [3]`(432, 55),   `ATLAS C [4]`(432, 55),
    `Cygni [1]`(433, 55),     `Cygni [2]`(433, 55),     `Cygni [3]`(433, 55),     `Cygni [4]`(433, 55),
    `Helvetios [1]`(434, 55), `Helvetios [2]`(434, 55), `Helvetios [3]`(434, 55), `Helvetios [4]`(434, 55),
    `Eridani [1]`(435, 55),   `Eridani [2]`(435, 55),   `Eridani [3]`(435, 55),   `Eridani [4]`(435, 55),
    `Sirius [1]`(436, 55),    `Sirius [2]`(436, 55),    `Sirius [3]`(436, 55),    `Sirius [4]`(436, 55),
    `Sadatoni [1]`(437, 55),  `Sadatoni [2]`(437, 55),  `Sadatoni [3]`(437, 55),  `Sadatoni [4]`(437, 55),
    `Persei [1]`(438, 55),    `Persei [2]`(438, 55),    `Persei [3]`(438, 55),    `Persei [4]`(438, 55),
    `Volantis [1]`(439, 55),  `Volantis [2]`(439, 55),  `Volantis [3]`(439, 55),  `Volantis [4]`(439, 55),
    `Alcyone [1]`(440, 55),   `Alcyone [2]`(440, 55),   `Alcyone [3]`(440, 55),   `Alcyone [4]`(440, 55),
    `Auriga [1]`(441, 55),    `Auriga [2]`(441, 55),    `Auriga [3]`(441, 55),    `Auriga [4]`(441, 55),
    `Bootes [1]`(442, 55),    `Bootes [2]`(442, 55),    `Bootes [3]`(442, 55),    `Bootes [4]`(442, 55),
    `Aquila [1]`(443, 55),    `Aquila [2]`(443, 55),    `Aquila [3]`(443, 55),    `Aquila [4]`(443, 55),
    `Orion [1]`(444, 55),     `Orion [2]`(444, 55),     `Orion [3]`(444, 55),     `Orion [4]`(444, 55),
    `Maia [1]`(445, 55),      `Maia [2]`(445, 55),      `Maia [3]`(445, 55),      `Maia [4]`(445, 55),

    `Escort VRU 1`(430), `Escort VRU 2`(431), `Escort VRU 3`(432),
    `Escort MMO 1`(433), `Escort MMO 2`(434), `Escort MMO 3`(435),
    `Escort EIC 1`(436), `Escort EIC 2`(437), `Escort EIC 3`(438),

    `Eternal Gate 1`(439),
    `Eternal Gate 2`(440),
    `Eternal Gate 3`(441),
    `Eternal Gate 4`(442),
    `Eternal Gate 5`(443),
    `Eternal Gate 6`(444),
    `Eternal Gate 7`(445),

    `GoP Easy 1`(450),
    `GoP Easy 2`(451),
    `GoP Easy 3`(452),
    `GoP Easy 4`(453),
    `GoP Easy 5`(454),
    `GoP Easy Final`(455, 1),

    `Eternal Blacklight 1`(460),
    `Eternal Blacklight 2`(461),
    `Eternal Blacklight 3`(462),
    `Eternal Blacklight 4`(463),
    `Eternal Blacklight 5`(464),
    `Eternal Blacklight 6`(465),

    `Astral Ascension 1`(466), `Astral Ascension 2`(467), `Astral Ascension 3`(468),

    `Plutus Trove of Riches Normal`(469), `Plutus Trove of Riches Easy`(470),
    `Treacherous Domain Easy`(471, 1), `Treacherous Domain Normal`(472, 1),


    `Escort VRU 4`(1439, 1),
    `Escort VRU 5`(1440, 1),
    `Escort VRU 6`(1441, 1),
    `Escort VRU 7`(1442, 1),
    `Escort VRU 8`(1443, 1),
    `Escort VRU 9`(1444, 1),
    `Escort VRU 10`(1445, 1),

    `Escort MMO 4`(1446, 1),
    `Escort MMO 5`(1447, 1),
    `Escort MMO 6`(1448, 1),
    `Escort MMO 7`(1449, 1),
    `Escort MMO 8`(1450, 1),
    `Escort MMO 9`(1451, 1),
    `Escort MMO 10`(1452, 1),

    `Escort EIC 4`(1453, 1),
    `Escort EIC 5`(1454, 1),
    `Escort EIC 6`(1455, 1),
    `Escort EIC 7`(1456, 1),
    `Escort EIC 8`(1457, 1),
    `Escort EIC 9`(1458, 1),
    `Escort EIC 10`(1459, 1),
}
