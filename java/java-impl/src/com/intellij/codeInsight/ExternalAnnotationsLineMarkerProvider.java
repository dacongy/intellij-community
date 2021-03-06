/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ApplyIntentionAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

public class ExternalAnnotationsLineMarkerProvider implements LineMarkerProvider {
  private static final Function<PsiElement, String> ourTooltipProvider = new Function<PsiElement, String>() {
    @Override
    public String fun(PsiElement nameIdentifier) {
      PsiModifierListOwner owner = (PsiModifierListOwner)nameIdentifier.getParent();
      
      boolean hasInferred = false;
      boolean hasExternal = false;
      for (PsiAnnotation annotation : findSignatureNonCodeAnnotations(owner)) {
        hasExternal |= AnnotationUtil.isExternalAnnotation(annotation);
        hasInferred |= AnnotationUtil.isInferredAnnotation(annotation);
      }
      
      String header;
      if (hasInferred && hasExternal) {
        header = "External and <i>inferred</i>";
      } else if (hasInferred) {
        header = "<i>Inferred</i>";
      } else {
        header = "External";
      }
      return XmlStringUtil.wrapInHtml(header + " annotations available. Full signature:<p>\n" + JavaDocInfoGenerator.generateSignature(owner));
    }
  };

  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull final PsiElement element) {
    PsiElement owner = element.getParent();
    if (!(owner instanceof PsiModifierListOwner) || !(owner instanceof PsiNameIdentifierOwner)) return null;
    if (owner instanceof PsiParameter || owner instanceof PsiLocalVariable) return null;

    // support non-Java languages where getNameIdentifier may return non-physical psi with the same range
    PsiElement nameIdentifier = ((PsiNameIdentifierOwner)owner).getNameIdentifier();
    if (nameIdentifier == null || !nameIdentifier.getTextRange().equals(element.getTextRange())) return null;

    if (findSignatureNonCodeAnnotations((PsiModifierListOwner)owner).isEmpty()) {
      return null;
    }

    return new LineMarkerInfo<PsiElement>(element, element.getTextRange().getStartOffset(),
                                          AllIcons.Gutter.ExtAnnotation,
                                          Pass.UPDATE_ALL,
                                          ourTooltipProvider, MyIconGutterHandler.INSTANCE,
                                          GutterIconRenderer.Alignment.RIGHT);
  }

  private static List<PsiAnnotation> findSignatureNonCodeAnnotations(PsiModifierListOwner owner) {
    List<PsiAnnotation> result = ContainerUtil.newArrayList(findOwnNonCodeAnnotations(owner));

    if (owner instanceof PsiMethod) {
      for (PsiParameter parameter : ((PsiMethod)owner).getParameterList().getParameters()) {
        result.addAll(findOwnNonCodeAnnotations(parameter));
      }
    }

    return result;
  }

  private static List<PsiAnnotation> findOwnNonCodeAnnotations(@NotNull PsiModifierListOwner element) {
    List<PsiAnnotation> result = ContainerUtil.newArrayList();
    Project project = element.getProject();
    PsiAnnotation[] externalAnnotations = ExternalAnnotationsManager.getInstance(project).findExternalAnnotations(element);
    if (externalAnnotations != null) {
      for (PsiAnnotation annotation : externalAnnotations) {
        if (isVisibleAnnotation(annotation)) {
          result.add(annotation);
        }
      }
    }
    for (PsiAnnotation annotation : InferredAnnotationsManager.getInstance(project).findInferredAnnotations(element)) {
      if (isVisibleAnnotation(annotation)) {
        result.add(annotation);
      }
    }
    return result;
  }

  private static boolean isVisibleAnnotation(@NotNull PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
    if (ref == null) return true;

    PsiElement target = ref.resolve();
    return !(target instanceof PsiClass) || JavaDocInfoGenerator.isDocumentedAnnotationType(target);
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {}

  private static class MyIconGutterHandler implements GutterIconNavigationHandler<PsiElement> {
    static final MyIconGutterHandler INSTANCE = new MyIconGutterHandler();

    @Override
    public void navigate(MouseEvent e, PsiElement nameIdentifier) {
      final PsiElement listOwner = nameIdentifier.getParent();
      final PsiFile containingFile = listOwner.getContainingFile();
      final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(listOwner);

      if (virtualFile != null && containingFile != null) {
        final Project project = listOwner.getProject();
        final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();

        if (editor != null) {
          editor.getCaretModel().moveToOffset(nameIdentifier.getTextOffset());
          final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

          if (file != null && virtualFile.equals(file.getVirtualFile())) {
            final JBPopup popup = createActionGroupPopup(containingFile, project, editor);
            if (popup != null) {
              popup.show(new RelativePoint(e));
            }
          }
        }
      }
    }

    @Nullable
    protected JBPopup createActionGroupPopup(PsiFile file, Project project, Editor editor) {
      final DefaultActionGroup group = new DefaultActionGroup();
      for (final IntentionAction action : IntentionManager.getInstance().getAvailableIntentionActions()) {
        if (action.isAvailable(project, editor, file)) {
          group.add(new ApplyIntentionAction(action, action.getText(), editor, file));
        }
      }

      if (group.getChildrenCount() > 0) {
        final DataContext context = SimpleDataContext.getProjectContext(null);
        return JBPopupFactory.getInstance()
          .createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
      }

      return null;
    }
  }
}
