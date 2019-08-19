package com.ditronic.securezipnotes

import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.junit.runners.Suite.SuiteClasses

@RunWith(Suite::class)
@SuiteClasses(ITest::class, SetupTests::class)
class OfflineTests

