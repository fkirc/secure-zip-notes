package com.ditronic.securezipnotes

import com.ditronic.securezipnotes.tests.ITest
import com.ditronic.securezipnotes.tests.ImportTests
import com.ditronic.securezipnotes.tests.SetupTests
import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.junit.runners.Suite.SuiteClasses

@RunWith(Suite::class)
@SuiteClasses(ITest::class, SetupTests::class, ImportTests::class)
class OfflineTests

