package utils

import tanvd.audit.model.external.types.AuditSerializer

data class TestClassFirst(val hash: Long = 1) {
    companion object serializer : AuditSerializer<TestClassFirst> {
        override fun display(value: TestClassFirst): String {
            return "TestClassFirstDisplay"
        }

        override fun deserialize(serializedString: String): TestClassFirst {
            if (serializedString == "TestClassFirstId") {
                return TestClassFirst()
            } else {
                throw IllegalArgumentException()
            }
        }

        override fun serialize(value: TestClassFirst): String {
            return "TestClassFirstId"
        }

    }
}

data class TestClassSecond(val hash: Long = 2) {
    companion object serializer : AuditSerializer<TestClassSecond> {
        override fun display(value: TestClassSecond): String {
            return "TestClassSecondDisplay"
        }

        override fun deserialize(serializedString: String): TestClassSecond {
            if (serializedString == "TestClassSecondId") {
                return TestClassSecond()
            } else {
                throw IllegalArgumentException()
            }
        }

        override fun serialize(value: TestClassSecond): String {
            return "TestClassSecondId"
        }

    }
}
