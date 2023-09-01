package me.rerere.unocssintellij.references

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.util.ui.ColorIcon
import me.rerere.unocssintellij.util.parseHexColor

class UnocssThemeConfigReference(element: PsiElement, textRange: TextRange) :
    PsiReferenceBase<PsiElement>(element, textRange),
    PsiPolyVariantReference {

    private val themeValue: String

    init {
        themeValue = element.text.substring(textRange.startOffset, textRange.endOffset)
            .trim('\'', '"')
    }

    override fun resolve(): PsiElement? {
        val resolveResults = multiResolve(false)
        return if (resolveResults.size == 1) resolveResults[0].element else null
    }

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val themeConfig = UnoConfigPsiHelper.findThemeConfig(element) ?: return emptyArray()

        val result = mutableListOf<ResolveResult>()
        val themeConfigValue = themeConfig.value
        if (themeConfigValue is JSObjectLiteralExpression) {
            val referencedProperty = UnoConfigPsiHelper
                .findThemeConfigProperty(themeConfigValue, themeValue.split("."), 0)

            if (referencedProperty != null) {
                result.add(PsiElementResolveResult(referencedProperty))
            }
        }

        return result.toTypedArray()
    }

    override fun getVariants(): Array<Any> {
        val themeConfig = UnoConfigPsiHelper.findThemeConfig(element) ?: return emptyArray()
        val themeConfigValue = themeConfig.value
        if (themeConfigValue !is JSObjectLiteralExpression) return emptyArray()

        val variants = mutableListOf<LookupElement>()
        // match path prefix
        val objectPath = themeValue.split(".").dropLast(1)
        val properties = if (objectPath.isEmpty()) {
            themeConfigValue.properties
        } else {
            val parentObj = UnoConfigPsiHelper.findThemeConfigProperty(themeConfigValue, objectPath, 0)
                ?: return emptyArray()

            val parentObjectValue = parentObj.value
            if (parentObjectValue is JSObjectLiteralExpression) {
                parentObjectValue.properties
            } else {
                emptyArray()
            }
        }
        if (properties.isEmpty()) {
            return emptyArray()
        }

        val prefixPath = objectPath.joinToString(".")
        for (prop in properties) {
            val propName = prop.name ?: continue
            val propValue = prop.value ?: continue

            val lookupString = if (prefixPath.isBlank()) propName else "$prefixPath.${propName}"
            val color = parseHexColor(propValue.text.trim('\'', '"'))

            variants.add(
                LookupElementBuilder
                    .create(prop, lookupString)
                    .withTypeText(themeConfig.containingFile.name)
                    .withTailText(computeTailText(prop), true)
                    .withIcon(if (color != null) ColorIcon(16, color) else AllIcons.Nodes.Property)
            )
        }

        return variants.toTypedArray()
    }

    private fun computeTailText(property: JSProperty): String {
        val value = property.value
        val content = if (value is JSObjectLiteralExpression) {
            "{...}"
        } else {
            value!!.text.trim('\'', '"')
        }
        return " $content "
    }
}