package config

import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.client.auth.ClientType
import com.github.m9w.config.module.HashMapConfig
import com.github.m9w.config.staticConfig
import com.github.m9w.config.view.ProxyView.build
import com.github.m9w.context.Context
import com.github.m9w.plugins.Loader
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigTest {
    companion object {
        private val logs = mutableListOf<ScriptDiagnostic>()
        private val config = Loader.dynamicModuleBuilder(Cfg::class, logs)?.newInstance ?: throw RuntimeException("Could not load config module")
        private val auth = Loader.dynamicModuleBuilder(Auth::class, logs)?.newInstance ?: throw RuntimeException("Could not load config module")
        private val configObj = config.instance as Cfg
        private val authObj = auth.instance as Auth
        private val moduleName = TestObject::class.qualifiedName!!

        @BeforeAll
        @JvmStatic
        fun init() {
            println("Module qualified name = $moduleName")
            Context.add(config, auth)
            configObj.innerMap.apply {
                this["STATIC/$moduleName/field3"] = "someElse"

                this["STATIC/$moduleName/field7"] = mutableSetOf("key1", "key2")
                this["STATIC/$moduleName/field7/key1"] = mutableSetOf("field1", "field2", "field3")
                this["STATIC/$moduleName/field7/key1/field1"] = "555"
                this["STATIC/$moduleName/field7/key1/field2"] = "777"

                this["STATIC/$moduleName/field7/key1/field3"] = mutableSetOf("key3", "key4")
                this["STATIC/$moduleName/field7/key1/field3/key3"] = "value7"
                this["STATIC/$moduleName/field7/key1/field3/key4"] = "value9"

                this["STATIC/$moduleName/field7/key2"] = mutableSetOf("field1", "field2", "field3")
                this["STATIC/$moduleName/field7/key2/field1"] = "222"
                this["STATIC/$moduleName/field7/key2/field2"] = "999"

                this["STATIC/$moduleName/field7/key2/field3"] = mutableSetOf("key3", "key4")
                this["STATIC/$moduleName/field7/key2/field3/key3"] = "value11"
                this["STATIC/$moduleName/field7/key2/field3/key4"] = "value10"

                this["STATIC/$moduleName/field10"] = "Test1"
                this["STATIC/$moduleName/field11"] = "NoNe"
            }
        }

        @AfterAll
        @JvmStatic
        fun after() {
            println(configObj.innerMap.toSortedMap().entries.joinToString("\n") { "${it.key.replace(moduleName, "...")}: ${it.value}: ${it.value!!::class.simpleName}" })
        }

        class Cfg : HashMapConfig() {
            val innerMap: HashMap<String, Any?> = map
        }

        class Auth : AuthenticationProvider {
            override var userID: Int = 112345
            override val host: String = ""
            override val sessionID: String = ""
            override val instanceId: Int = 1
            override var mapId: Int = 0
            override val type: ClientType = ClientType.FLASH
            override val serialized: String = ""
        }

        private object TestObject {
            var field1: String? by staticConfig("defaultValue")
            var field2: Int? by staticConfig(15)
            var field3: String by staticConfig("defaultValue")
            var field4: MutableMap<String, String> by staticConfig(
                mutableMapOf(
                    "key1" to "value1",
                    "key2" to "value2"
                )
            )
            var field5: MutableMap<String, MutableMap<String, String>> by staticConfig(
                mutableMapOf(
                    "key1" to mutableMapOf(
                        "key3" to "val4"
                    ), "key2" to mutableMapOf("key4" to "val8")
                )
            )
            var field6: Custom by staticConfig(build<Custom> { field1 = "111"; field2 = "222" })
            var field7: MutableMap<String, Custom> by staticConfig(
                mutableMapOf(
                    "key1" to build<Custom> {
                        field1 = "333"; field2 = "444"; field3 = mutableMapOf("key1" to "value1", "key2" to "value2")
                    },
                    "key2" to build<Custom> {
                        field1 = "555"; field2 = "666"; field3 = mutableMapOf("key3" to "value3", "key4" to "value4")
                    },
                    "key4" to build<Custom> { field1 = "0000" },
                )
            )
            var field8: MutableMap<Int, String> by staticConfig(mutableMapOf(11 to "ff", 22 to "fe"))
            var field9: MutableSet<String> by staticConfig(mutableSetOf("11", "22"))
            var field10: TestEnum by staticConfig(TestEnum.Test0)
            var field11: TestEnum by staticConfig(TestEnum.Test0)
            var field12: TestEnum? by staticConfig(TestEnum.Test0)
        }

        private interface Custom {
            var field1: String
            var field2: String?
            var field3: MutableMap<String, String>
        }

        private enum class TestEnum {
            Test0, Test1, Test2, Test3
        }
    }

    @BeforeEach
    fun setUp() {
        //after()
    }

    @Test
    fun test_01() {
        assertEquals("222", TestObject.field7["key2"]!!.field1)
    }
    @Test
    fun test_02() {
        assertEquals("999", TestObject.field7["key2"]!!.field2)
    }
    @Test
    fun test_03() {
        assertEquals("value11", TestObject.field7["key2"]!!.field3["key3"])
    }
    @Test
    fun test_04() {
        assertEquals("value10", TestObject.field7["key2"]!!.field3["key4"])
    }
    @Test
    fun test_05() {
        assertEquals("defaultValue", TestObject.field1)
    }
    @Test
    fun test_06() {
        assertEquals(15, TestObject.field2)
    }
    @Test
    fun test_07() {
        assertEquals("someElse", TestObject.field3)
    }
    @Test
    fun test_08() {
        assertEquals("value1", TestObject.field4["key1"])
    }
    @Test
    fun test_09() {
        assertEquals("value2", TestObject.field4["key2"])
    }
    @Test
    fun test_10() {
        assertEquals("val4", TestObject.field5["key1"]!!["key3"])
    }
    @Test
    fun test_11() {
        assertEquals("val8", TestObject.field5["key2"]!!["key4"])
    }
    @Test
    fun test_12() {
        assertEquals("222", TestObject.field6.field2)
    }
    @Test
    fun test_13() {
        Assertions.assertNotEquals("666", TestObject.field7["key2"]!!.field2)
    }
    @Test
    fun test_14() {
        assertTrue(TestObject.field4.containsKey("key1"))
    }
    @Test
    fun test_15() {
        assertTrue(TestObject.field4.containsKey("key2"))
    }
    @Test
    fun test_16() {
        TestObject.field1 = "customValue"
        assertEquals("customValue", TestObject.field1)
    }

    @Test
    fun test_17() {
        TestObject.field2 = 16
        assertEquals(16, TestObject.field2)
    }

    @Test
    fun test_18() {
        TestObject.field4["key2"] = "value3"
        assertEquals("value3", TestObject.field4["key2"])
    }

    @Test
    fun test_19() {
        TestObject.field5["key2"]!!["key4"] = "val9"
        assertEquals("val9", TestObject.field5["key2"]!!["key4"])
    }

    @Test
    fun test_20() {
        TestObject.field6.field1 = "5555"
        assertEquals("5555", TestObject.field6.field1)
    }

    @Test
    fun test_21() {
        TestObject.field7["key1"]!!.field1 = "8888"
        assertEquals("8888", TestObject.field7["key1"]!!.field1)
    }

    @Test
    fun test_22() {
        TestObject.field7.remove("key1")
        assertEquals(null, TestObject.field7["key1"])
    }

    @Test
    fun test_23() {
        TestObject.field7["key2"] = build<Custom> { field1 = "customValue"; field3 = mutableMapOf("key44" to "value44") }
        assertEquals("customValue", TestObject.field7["key2"]!!.field1)
    }

    @Test
    fun test_24() {
        TestObject.field7["key2"] = build<Custom> { field1 = "customValue"; field3 = mutableMapOf("key33" to "value44") }
        assertEquals(null, TestObject.field7["key2"]!!.field2)
    }

    @Test
    fun test_25() {
        assertEquals("value44", TestObject.field7["key2"]!!.field3["key33"])
    }

    @Test
    fun test_26() {
        TestObject.field1 = null
        assertEquals(null, TestObject.field1)
    }

    @Test
    fun test_27() {
        TestObject.field2 = null
        assertEquals(null, TestObject.field2)
    }

    @Test
    fun test_28() {
        assertEquals("ff", TestObject.field8[11])
    }

    @Test
    fun test_29() {
        assertEquals("fe", TestObject.field8[22])
    }

    @Test
    fun test_30() {
        TestObject.field8[22] = "abc"
        assertEquals("abc", TestObject.field8[22])
    }

    @Test
    fun test_31() {
        TestObject.field8[33] = "wer"
        assertEquals("wer", TestObject.field8[33])
    }

    @Test
    fun test_32() {
        TestObject.field8.remove(11)
        assertEquals(null, TestObject.field8[11])
    }

    @Test
    fun test_33() {
        assertEquals(true, TestObject.field9.contains("11"))
    }

    @Test
    fun test_34() {
        assertEquals(true, TestObject.field9.contains("22"))
    }

    @Test
    fun test_35() {
        assertEquals(false, TestObject.field9.contains("33"))
    }

    @Test
    fun test_36() {
        TestObject.field9.add("33")
        assertEquals(true, TestObject.field9.contains("33"))
    }

    @Test
    fun test_37() {
        assertEquals(TestEnum.Test1, TestObject.field10)
    }

    @Test
    fun test_38() {
        assertEquals(TestEnum.Test0, TestObject.field11)
    }

    @Test
    fun test_39() {
        assertEquals(TestEnum.Test0, TestObject.field12)
    }

    @Test
    fun test_40() {
        TestObject.field10 = TestEnum.Test2
        assertEquals(TestEnum.Test2, TestObject.field10)
    }

    @Test
    fun test_41() {
        TestObject.field11 = TestEnum.Test2
        assertEquals(TestEnum.Test2, TestObject.field11)
    }

    @Test
    fun test_42() {
        TestObject.field12 = TestEnum.Test2
        assertEquals(TestEnum.Test2, TestObject.field12)
    }

    @Test
    fun test_43() {
        TestObject.field12 = null
        assertEquals(null, TestObject.field12)
    }

    @AfterEach
    fun tearDown() {
        //after()
    }
}