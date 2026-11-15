package cn.lineai.feature.workspace;

import org.junit.Test;

import cn.lineai.core.model.Project;

import static org.junit.Assert.assertEquals;

public class WorkspaceNamesTest {

    private static final String DEFAULT_LABEL = "Tap to set workspace";

    @Test public void keepsNonLatinNamesReadable() {
        assertEquals("我的项目", WorkspaceNames.folder("我的项目"));
        assertEquals("проект", WorkspaceNames.folder("проект"));
    }

    @Test public void replacesOnlyIllegalPathCharacters() {
        assertEquals("A-B-C", WorkspaceNames.folder("A/B:C"));
        assertEquals("my-project", WorkspaceNames.folder("my project"));
        assertEquals("a-b", WorkspaceNames.folder("a\\b"));
    }

    @Test public void rejectsNamesThatCannotBecomeDirectories() {
        assertEquals("", WorkspaceNames.folder("   "));
        assertEquals("", WorkspaceNames.folder(".."));
        assertEquals("", WorkspaceNames.folder("."));
    }

    @Test public void truncatesLongNames() {
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < 80; index++) name.append('a');
        assertEquals(60, WorkspaceNames.folder(name.toString()).length());
    }

    @Test public void labelsTheBuiltInProjectWithTheDefaultCaption() {
        assertEquals(DEFAULT_LABEL, WorkspaceNames.label(null, DEFAULT_LABEL));
        assertEquals(DEFAULT_LABEL, WorkspaceNames.label(
                project(Project.DEFAULT_ID, "LineCode"), DEFAULT_LABEL));
    }

    @Test public void keepsUserProjectNamesAsLabels() {
        Project project = project("external-1", "my project");
        assertEquals("my project", WorkspaceNames.label(project, DEFAULT_LABEL));
    }

    private static Project project(String id, String name) {
        return new Project(id, name, "/tmp/" + id, false, 0L, 0L);
    }
}
