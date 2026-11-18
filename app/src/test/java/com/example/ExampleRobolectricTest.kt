package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.FoldAssignment
import com.example.core.model.PaperSpec
import com.example.data.model.OrigamiTemplateId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name matches ATP Origami Studio`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ATP Origami Studio", appName)
  }

  @Test
  fun `project repository creates saves and reloads crease pattern`() = runTest {
    val app = ApplicationProvider.getApplicationContext<OrigamiStudioApp>()
    val repo = app.container.projectRepository

    val created =
      repo.createProject(
        name = "Crane Study",
        paper = PaperSpec(widthMm = 200.0, heightMm = 200.0, gridSpacingMm = 25.0),
        templateId = OrigamiTemplateId.PRELIMINARY_BASE,
      )

    val list = repo.observeProjects("").first()
    assertTrue(list.any { it.id == created.id && it.name == "Crane Study" })

    val loaded = repo.loadProject(created.id)
    assertNotNull(loaded)
    assertEquals(created.vertices.size, loaded!!.vertices.size)
    assertEquals(created.edges.size, loaded.edges.size)
    assertTrue(loaded.edges.any { it.assignment == FoldAssignment.MOUNTAIN })
    assertTrue(loaded.edges.any { it.assignment == FoldAssignment.VALLEY })

    repo.deleteProject(created.id)
  }
}
