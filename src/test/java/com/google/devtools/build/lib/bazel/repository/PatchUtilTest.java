// Copyright 2019 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.bazel.repository;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.build.lib.testutil.MoreAsserts.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.testutil.Scratch;
import com.google.devtools.build.lib.vfs.FileSystem;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.inmemoryfs.InMemoryFileSystem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

import difflib.PatchFailedException;

/**
 * Tests for {@link PatchUtil}.
 */
@RunWith(JUnit4.class)
public class PatchUtilTest {

  private FileSystem fs;
  private Scratch scratch;
  private Path root;

  @Before
  public final void initializeFileSystemAndDirectories() throws Exception {
    fs = new InMemoryFileSystem();
    scratch = new Scratch(fs, "/root");
    root = scratch.dir("/root");
  }

  @Test
  public void testAddFile() throws IOException, PatchFailedException {
    Path patchFile = scratch.file("/root/patchfile",
        "diff --git a/newfile b/newfile",
        "new file mode 100644",
        "index 0000000..f742c88",
        "--- /dev/null",
        "+++ b/newfile",
        "@@ -0,0 +1,2 @@",
        "+I'm a new file",
        "+hello, world",
        "-- ",
        "2.21.0.windows.1");
    PatchUtil.apply(patchFile, 1, root);
    Path newFile = root.getRelative("newfile");
    ImmutableList<String> newFileContent = ImmutableList.of(
        "I'm a new file",
        "hello, world"
    );
    assertThat(PatchUtil.readFile(newFile)).containsExactlyElementsIn(newFileContent);
  }

  @Test
  public void testDeleteFile() throws IOException, PatchFailedException {
    Path oldFile = scratch.file("/root/oldfile",
        "I'm an old file",
        "bye, world"
        );
    Path patchFile = scratch.file("/root/patchfile",
        "diff --git a/oldfile b/oldfile",
        "deleted file mode 100644",
        "index f742c88..0000000",
        "--- a/oldfile",
        "+++ /dev/null",
        "@@ -1,2 +0,0 @@",
        "-I'm an old file",
        "-bye, world",
        "-- ",
        "2.21.0.windows.1");
    PatchUtil.apply(patchFile, 1, root);
    assertThat(oldFile.exists()).isFalse();
  }

  @Test
  public void testGitFormatPatching() throws IOException, PatchFailedException {
    Path foo = scratch.file("/root/foo.cc",
        "#include <stdio.h>",
        "",
        "void main(){",
        "  printf(\"Hello foo\");",
        "}");
    Path bar = scratch.file("/root/bar.cc",
        "void lib(){",
        "  printf(\"Hello bar\");",
        "}");
    Path patchFile = scratch.file("/root/patchfile",
        "From d205551eab3350afdb380f90ef83442ffcc0e22b Mon Sep 17 00:00:00 2001",
        "From: Yun Peng <pcloudy@google.com>",
        "Date: Thu, 6 Jun 2019 11:34:08 +0200",
        "Subject: [PATCH] 2",
        "",
        "---",
        " bar.cc | 2 +-",
        " foo.cc | 1 +",
        " 2 files changed, 2 insertions(+), 1 deletion(-)",
        "",
        "diff --git a/bar.cc b/bar.cc",
        "index e77137b..36dc9ab 100644",
        "--- a/bar.cc",
        "+++ b/bar.cc",
        "@@ -1,3 +1,3 @@",
        " void lib(){",
        "-  printf(\"Hello bar\");",
        "+  printf(\"Hello patch\");",
        " }",
        "diff --git a/foo.cc b/foo.cc",
        "index f3008f9..ec4aaa0 100644",
        "--- a/foo.cc",
        "+++ b/foo.cc",
        "@@ -2,4 +2,5 @@",
        " ",
        " void main(){",
        "   printf(\"Hello foo\");",
        "+  printf(\"Hello from patch\");",
        " }",
        "-- ",
        "2.21.0.windows.1",
        "",
        "");
    PatchUtil.apply(patchFile, 1, root);
    ImmutableList<String> newFoo = ImmutableList.of(
        "#include <stdio.h>",
        "",
        "void main(){",
        "  printf(\"Hello foo\");",
        "  printf(\"Hello from patch\");",
        "}"
    );
    ImmutableList<String> newBar = ImmutableList.of(
        "void lib(){",
        "  printf(\"Hello patch\");",
        "}"
    );
    assertThat(PatchUtil.readFile(foo)).containsExactlyElementsIn(newFoo);
    assertThat(PatchUtil.readFile(bar)).containsExactlyElementsIn(newBar);
  }

  @Test
  public void testMatchWithOffset() throws IOException, PatchFailedException {
    Path foo = scratch.file("/root/foo.cc",
        "#include <stdio.h>",
        "",
        "void main(){",
        "  printf(\"Hello foo\");",
        "}");
    Path patchFile = scratch.file("/root/patchfile",
        "diff --git a/foo.cc b/foo.cc",
        "index f3008f9..ec4aaa0 100644",
        "--- a/foo.cc",
        "+++ b/foo.cc",
        "@@ -6,4 +6,5 @@", // Should match with offset -4, original is "@@ -2,4 +2,5 @@"
        " ",
        " void main(){",
        "   printf(\"Hello foo\");",
        "+  printf(\"Hello from patch\");",
        " }");
    PatchUtil.apply(patchFile, 1, root);
    ImmutableList<String> newFoo = ImmutableList.of(
        "#include <stdio.h>",
        "",
        "void main(){",
        "  printf(\"Hello foo\");",
        "  printf(\"Hello from patch\");",
        "}"
    );
    assertThat(PatchUtil.readFile(foo)).containsExactlyElementsIn(newFoo);
  }

  @Test
  public void testMultipleChunksWithDifferentOffset() throws IOException, PatchFailedException {
    Path foo = scratch.file("/root/foo",
        "1",
        "3",
        "4",
        "5",
        "6",
        "7",
        "8",
        "9",
        "10",
        "11",
        "13",
        "14");
    Path patchFile = scratch.file("/root/patchfile",
        "diff --git a/foo b/foo",
        "index c20ab12..b83bdb1 100644",
        "--- a/foo",
        "+++ b/foo",
        "@@ -3,4 +3,5 @@", // Should match with offset -2, original is "@@ -1,4 +1,5 @@"
        " 1",
        "+2",
        " 3",
        " 4",
        " 5",
        "@@ -4,5 +5,6 @@", // Should match with offset 4, original is "@@ -8,4 +9,5 @@"
        " 9",
        " 10",
        " 11",
        "+12",
        " 13",
        " 14");
    PatchUtil.apply(patchFile, 1, root);
    ImmutableList<String> newFoo = ImmutableList.of(
        "1",
        "2",
        "3",
        "4",
        "5",
        "6",
        "7",
        "8",
        "9",
        "10",
        "11",
        "12",
        "13",
        "14"
    );
    assertThat(PatchUtil.readFile(foo)).containsExactlyElementsIn(newFoo);
  }

  @Test
  public void testChunkDoesNotMatch() throws IOException {
    Path foo = scratch.file("/root/foo.cc",
        "#include <stdio.h>",
        "",
        "void main(){",
        "  printf(\"Hello foo\");",
        "}");
    Path patchFile = scratch.file("/root/patchfile",
        "diff --git a/foo.cc b/foo.cc",
        "index f3008f9..ec4aaa0 100644",
        "--- a/foo.cc",
        "+++ b/foo.cc",
        "@@ -2,4 +2,5 @@",
        " ",
        " void main(){",
        "   printf(\"Hello bar\");", // Should be "Hello foo"
        "+  printf(\"Hello from patch\");",
        " }");
    PatchFailedException expected =
        assertThrows(
            PatchFailedException.class,
            () -> PatchUtil.apply(patchFile, 1, root));
    assertThat(expected).hasMessageThat()
        .contains("Incorrect Chunk: the chunk content doesn't match the target");
  }

  @Test
  public void testWrongChunkFormat1() throws IOException {
    Path foo = scratch.file("/root/foo.cc",
        "#include <stdio.h>",
        "",
        "void main(){",
        "  printf(\"Hello foo\");",
        "}");
    Path patchFile = scratch.file("/root/patchfile",
        "diff --git a/foo.cc b/foo.cc",
        "index f3008f9..ec4aaa0 100644",
        "--- a/foo.cc",
        "+++ b/foo.cc",
        "@@ -2,4 +2,5 @@",
        " ",
        " void main(){",
        "   printf(\"Hello foo\");",
        "+  printf(\"Hello from patch\");",
        "+", // Adding this line will cause the chunk body not matching the header "@@ -2,4 +2,5 @@"
        " }");
    PatchFailedException expected =
        assertThrows(
            PatchFailedException.class,
            () -> PatchUtil.apply(patchFile, 1, root));
    assertThat(expected).hasMessageThat()
        .contains("Wrong chunk detected near line 11:  }");
  }

  @Test
  public void testWrongChunkFormat2() throws IOException {
    Path foo = scratch.file("/root/foo.cc",
        "#include <stdio.h>",
        "",
        "void main(){",
        "  printf(\"Hello foo\");",
        "}");
    Path patchFile = scratch.file("/root/patchfile",
        "diff --git a/foo.cc b/foo.cc",
        "index f3008f9..ec4aaa0 100644",
        "--- a/foo.cc",
        "+++ b/foo.cc",
        "@@ -2,4 +2,5 @@",
        " ",
        " void main(){",
        "   printf(\"Hello foo\");",
        "+  printf(\"Hello from patch\");");
    PatchFailedException expected =
        assertThrows(
            PatchFailedException.class,
            () -> PatchUtil.apply(patchFile, 1, root));
    assertThat(expected).hasMessageThat()
        .contains("Expecting more chunk line at line 10");
  }
}