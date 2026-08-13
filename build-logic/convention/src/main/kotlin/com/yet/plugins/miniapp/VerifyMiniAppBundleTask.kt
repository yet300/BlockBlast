package com.yet.plugins.miniapp

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Verification task has no outputs")
internal abstract class VerifyMiniAppBundleTask : DefaultTask() {
    @get:Input
    abstract val expectedProjectPaths: ListProperty<String>

    @get:Input
    abstract val actualProjectPaths: ListProperty<String>

    @get:Input
    abstract val conventionProjectPaths: ListProperty<String>

    @TaskAction
    fun verify() {
        val expected = expectedProjectPaths.get()
        val actual = actualProjectPaths.get()
        val convention = conventionProjectPaths.get().toSet()
        val duplicateDependencies = actual.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
        val missing = expected.filterNot(actual::contains).sorted()
        val unexpected = actual.filterNot(expected::contains).distinct().sorted()
        val withoutConvention = expected.filterNot(convention::contains).sorted()
        val membershipIsClean =
            duplicateDependencies.isEmpty() &&
                missing.isEmpty() &&
                unexpected.isEmpty() &&
                withoutConvention.isEmpty()
        val outOfOrder = membershipIsClean && actual != expected
        check(
            membershipIsClean && !outOfOrder,
        ) {
            "Mini-app bundle mismatch: missing=$missing, unexpected=$unexpected, " +
                "duplicates=$duplicateDependencies, withoutLogicaMiniApp=$withoutConvention, " +
                "outOfOrder=$outOfOrder"
        }
    }
}
