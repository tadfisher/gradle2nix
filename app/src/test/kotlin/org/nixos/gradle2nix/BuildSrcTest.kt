package org.nixos.gradle2nix

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import strikt.api.expectThat
import strikt.assertions.containsKey

object BuildSrcTest : Spek({
    fixture("buildsrc/plugin-in-buildsrc/kotlin")
    val fixture: Fixture by memoized()

    describe("project with plugin in buildSrc") {
        fixture.run()

        it("should include buildSrc in gradle env", timeout = 0) {
            expectThat(fixture.env()).containsKey("buildSrc")
        }
    }
})