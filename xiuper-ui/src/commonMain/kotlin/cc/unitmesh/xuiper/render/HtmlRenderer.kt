package cc.unitmesh.xuiper.render

import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.serialization.json.jsonPrimitive

/**
 * HTML Renderer for NanoIR
 *
 * Renders NanoIR components to static HTML.
 * Useful for server-side rendering and testing.
 *
 * Implements NanoRenderer<String> with component-specific methods.
 */
class HtmlRenderer(
    private val context: RenderContext = RenderContext()
) : NanoRenderer<String> {

    private fun renderFlexStyleAttribute(ir: NanoIR): String {
        val raw = ir.props["flex"]?.jsonPrimitive?.content ?: return ""
        val flex = raw.toFloatOrNull() ?: return ""
        if (flex <= 0f) return ""
        // min-width:0 prevents long text from overflowing in flex rows.
        return " style=\"flex: $flex 1 0%; min-width: 0;\""
    }

    private fun escapeHtmlAttr(value: String): String {
        return buildString(value.length) {
            for (c in value) {
                when (c) {
                    '&' -> append("&amp;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    else -> append(c)
                }
            }
        }
    }

    override fun render(ir: NanoIR): String {
        return buildString {
            append("<!DOCTYPE html>\n<html>\n<head>\n")
            append("<meta charset=\"UTF-8\">\n")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
            append("<style>\n")
            append(generateCss())
            append("</style>\n")
            append("</head>\n<body>\n")
            append(renderNode(ir))
            append("</body>\n</html>")
        }
    }

    override fun renderNode(ir: NanoIR): String {
        return when (ir.type) {
            "Component" -> renderComponent(ir)
            "VStack" -> renderVStack(ir)
            "HStack" -> renderHStack(ir)
            "Card" -> renderCard(ir)
            "Form" -> renderForm(ir)
            "Text" -> renderText(ir)
            "Image" -> renderImage(ir)
            "Badge" -> renderBadge(ir)
            "Divider" -> renderDivider(ir)
            "Button" -> renderButton(ir)
            "Input" -> renderInput(ir)
            "Checkbox" -> renderCheckbox(ir)
            "TextArea" -> renderTextArea(ir)
            "Select" -> renderSelect(ir)
            // P0: Core Form Input Components
            "DatePicker" -> renderDatePicker(ir)
            "Radio" -> renderRadio(ir)
            "RadioGroup" -> renderRadioGroup(ir)
            "Switch" -> renderSwitch(ir)
            "NumberInput" -> renderNumberInput(ir)
            // P0: Feedback Components
            "Modal" -> renderModal(ir)
            "Alert" -> renderAlert(ir)
            "Progress" -> renderProgress(ir)
            "Spinner" -> renderSpinner(ir)
            // Tier 1-3: GenUI Components
            "SplitView" -> renderSplitView(ir)
            "GenCanvas" -> renderGenCanvas(ir)
            "SmartTextField" -> renderSmartTextField(ir)
            "Slider" -> renderSlider(ir)
            "DateRangePicker" -> renderDateRangePicker(ir)
            "DataChart" -> renderDataChart(ir)
            "DataTable" -> renderDataTable(ir)
            "Conditional" -> renderConditional(ir)
            "ForLoop" -> renderForLoop(ir)
            else -> renderUnknown(ir)
        }
    }

    // ============================================================================
    // Layout Components
    // ============================================================================

    override fun renderVStack(ir: NanoIR): String {
        val spacing = ir.props["spacing"]?.jsonPrimitive?.content ?: "md"
        val align = ir.props["align"]?.jsonPrimitive?.content ?: "stretch"
        return buildString {
            append("<div class=\"nano-vstack spacing-$spacing align-$align\"")
            append(renderFlexStyleAttribute(ir))
            append(">\n")
            ir.children?.forEach { append(renderNode(it)) }
            append("</div>\n")
        }
    }

    override fun renderHStack(ir: NanoIR): String {
        val spacing = ir.props["spacing"]?.jsonPrimitive?.content ?: "md"
        val align = ir.props["align"]?.jsonPrimitive?.content ?: "center"
        val justify = ir.props["justify"]?.jsonPrimitive?.content ?: "start"
        val wrapRaw = ir.props["wrap"]?.jsonPrimitive?.content
        val wrapEnabled = wrapRaw == "wrap" || wrapRaw.equals("true", ignoreCase = true)
        val wrapClass = if (wrapEnabled) " wrap" else ""
        return buildString {
            append("<div class=\"nano-hstack spacing-$spacing align-$align justify-$justify$wrapClass\"")
            append(renderFlexStyleAttribute(ir))
            append(">\n")
            ir.children?.forEach { append(renderNode(it)) }
            append("</div>\n")
        }
    }

    // ============================================================================
    // Container Components
    // ============================================================================

    override fun renderCard(ir: NanoIR): String {
        val padding = ir.props["padding"]?.jsonPrimitive?.content ?: "md"
        val shadow = ir.props["shadow"]?.jsonPrimitive?.content ?: "sm"
        return buildString {
            append("<div class=\"nano-card padding-$padding shadow-$shadow\"")
            append(renderFlexStyleAttribute(ir))
            append(">\n")
            ir.children?.forEach { append(renderNode(it)) }
            append("</div>\n")
        }
    }

    override fun renderForm(ir: NanoIR): String {
        val onSubmit = ir.props["onSubmit"]?.jsonPrimitive?.content
        return buildString {
            append("<form class=\"nano-form\"")
            append(renderFlexStyleAttribute(ir))
            if (onSubmit != null) append(" data-action=\"$onSubmit\"")
            append(">\n")
            ir.children?.forEach { append(renderNode(it)) }
            append("</form>\n")
        }
    }

    // ============================================================================
    // Content Components
    // ============================================================================

    override fun renderText(ir: NanoIR): String {
        val content = ir.props["content"]?.jsonPrimitive?.content ?: ""
        val style = ir.props["style"]?.jsonPrimitive?.content ?: "body"
        val tag = when (style) {
            "h1" -> "h1"
            "h2" -> "h2"
            "h3" -> "h3"
            "h4" -> "h4"
            "caption" -> "small"
            else -> "p"
        }
        return "<$tag class=\"nano-text style-$style\">$content</$tag>\n"
    }

    override fun renderImage(ir: NanoIR): String {
        val src = ir.props["src"]?.jsonPrimitive?.content ?: ""
        val aspect = ir.props["aspect"]?.jsonPrimitive?.content
        val radius = ir.props["radius"]?.jsonPrimitive?.content ?: "none"
        val aspectClass = aspect?.replace("/", "-") ?: "auto"
        val alt = ir.props["alt"]?.jsonPrimitive?.content ?: "Image"
        return "<img src=\"$src\" class=\"nano-image aspect-$aspectClass radius-$radius\" alt=\"$alt\">\n"
    }

    override fun renderBadge(ir: NanoIR): String {
        val text = ir.props["text"]?.jsonPrimitive?.content ?: ""
        val color = ir.props["color"]?.jsonPrimitive?.content ?: "default"
        return "<span class=\"nano-badge color-$color\">$text</span>\n"
    }

    override fun renderDivider(ir: NanoIR): String {
        return "<hr class=\"nano-divider\">\n"
    }

    // ============================================================================
    // Input Components
    // ============================================================================

    override fun renderButton(ir: NanoIR): String {
        val label = ir.props["label"]?.jsonPrimitive?.content ?: ""
        val intent = ir.props["intent"]?.jsonPrimitive?.content ?: "default"
        val icon = ir.props["icon"]?.jsonPrimitive?.content
        val disabledIf = ir.props["disabled_if"]?.jsonPrimitive?.content
        val actionAttr = renderActionAttribute(ir)
        val bindingAttr = renderBindingAttribute(ir)
        val disabledIfAttr = disabledIf?.takeIf { it.isNotBlank() }?.let {
            " data-disabled-if=\"${escapeHtmlAttr(it)}\""
        } ?: ""

        return buildString {
            append("<button class=\"nano-button intent-$intent\"")
            append(actionAttr)
            append(bindingAttr)
            append(disabledIfAttr)
            append(">")
            if (icon != null) append("<span class=\"icon\">$icon</span> ")
            append(label)
            append("</button>\n")
        }
    }

    override fun renderInput(ir: NanoIR): String {
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: ""
        val type = ir.props["type"]?.jsonPrimitive?.content ?: "text"
        val bindingAttr = renderBindingAttribute(ir)
        val actionAttr = renderActionAttribute(ir)

        return buildString {
            append("<input type=\"$type\" class=\"nano-input\" placeholder=\"$placeholder\"")
            append(bindingAttr)
            append(actionAttr)
            append(">\n")
        }
    }

    override fun renderCheckbox(ir: NanoIR): String {
        val label = ir.props["label"]?.jsonPrimitive?.content
        val bindingAttr = renderBindingAttribute(ir)
        val actionAttr = renderActionAttribute(ir)

        return if (label != null) {
            buildString {
                append("<label class=\"nano-checkbox-wrapper\">")
                append("<input type=\"checkbox\" class=\"nano-checkbox\"")
                append(bindingAttr)
                append(actionAttr)
                append("><span>$label</span></label>\n")
            }
        } else {
            buildString {
                append("<input type=\"checkbox\" class=\"nano-checkbox\"")
                append(bindingAttr)
                append(actionAttr)
                append(">\n")
            }
        }
    }

    override fun renderTextArea(ir: NanoIR): String {
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: ""
        val rows = ir.props["rows"]?.jsonPrimitive?.content ?: "4"
        val bindingAttr = renderBindingAttribute(ir)
        val actionAttr = renderActionAttribute(ir)

        return buildString {
            append("<textarea class=\"nano-textarea\" placeholder=\"$placeholder\" rows=\"$rows\"")
            append(bindingAttr)
            append(actionAttr)
            append("></textarea>\n")
        }
    }

    override fun renderSelect(ir: NanoIR): String {
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content
        val options = ir.props["options"]?.jsonPrimitive?.content
        val bindingAttr = renderBindingAttribute(ir)
        val actionAttr = renderActionAttribute(ir)

        return buildString {
            append("<select class=\"nano-select\"")
            append(bindingAttr)
            append(actionAttr)
            append(">\n")
            if (placeholder != null) {
                append("  <option value=\"\" disabled selected>$placeholder</option>\n")
            }
            // Options would be populated dynamically
            if (options != null) {
                append("  <!-- options: $options -->\n")
            }
            append("</select>\n")
        }
    }

    // ============================================================================
    // Control Flow Components
    // ============================================================================

    override fun renderConditional(ir: NanoIR): String {
        // In static HTML, we render the then branch
        // Dynamic evaluation would happen on client-side
        return buildString {
            append("<!-- if: ${ir.condition} -->\n")
            ir.children?.forEach { append(renderNode(it)) }
            append("<!-- endif -->\n")
        }
    }

    override fun renderForLoop(ir: NanoIR): String {
        val loop = ir.loop
        return buildString {
            append("<!-- for ${loop?.variable} in ${loop?.iterable} -->\n")
            ir.children?.forEach { append(renderNode(it)) }
            append("<!-- endfor -->\n")
        }
    }

    // ============================================================================
    // Meta Components
    // ============================================================================

    override fun renderComponent(ir: NanoIR): String {
        val name = ir.props["name"]?.jsonPrimitive?.content ?: "Component"
        return buildString {
            append("<div class=\"nano-component\" data-name=\"$name\">\n")
            ir.children?.forEach { append(renderNode(it)) }
            append("</div>\n")
        }
    }

    override fun renderUnknown(ir: NanoIR): String {
        return "<!-- Unknown component: ${ir.type} -->\n"
    }

    // ============================================================================
    // P0: Core Form Input Components
    // ============================================================================

    fun renderDatePicker(ir: NanoIR): String {
        val format = ir.props["format"]?.jsonPrimitive?.content ?: "YYYY-MM-DD"
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: ""
        val bindingAttr = renderBindingAttribute(ir)
        val actionAttr = renderActionAttribute(ir)
        return "<input type=\"date\" class=\"nano-datepicker\" placeholder=\"$placeholder\" format=\"$format\"$bindingAttr$actionAttr>\n"
    }

    fun renderRadio(ir: NanoIR): String {
        val option = ir.props["option"]?.jsonPrimitive?.content ?: ""
        val label = ir.props["label"]?.jsonPrimitive?.content
        val name = ir.props["name"]?.jsonPrimitive?.content ?: ""
        val bindingAttr = renderBindingAttribute(ir)
        return buildString {
            append("<label class=\"nano-radio-wrapper\">")
            append("<input type=\"radio\" class=\"nano-radio\" name=\"$name\" value=\"$option\"$bindingAttr>")
            if (label != null) append("<span>$label</span>")
            append("</label>\n")
        }
    }

    fun renderRadioGroup(ir: NanoIR): String {
        val name = ir.props["name"]?.jsonPrimitive?.content ?: ""
        val bindingAttr = renderBindingAttribute(ir)
        return buildString {
            append("<div class=\"nano-radiogroup\"$bindingAttr>\n")
            ir.children?.forEach { append(renderNode(it)) }
            append("</div>\n")
        }
    }

    fun renderSwitch(ir: NanoIR): String {
        val label = ir.props["label"]?.jsonPrimitive?.content
        val bindingAttr = renderBindingAttribute(ir)
        val actionAttr = renderActionAttribute(ir)
        return buildString {
            append("<label class=\"nano-switch-wrapper\">")
            append("<input type=\"checkbox\" class=\"nano-switch\" role=\"switch\"$bindingAttr$actionAttr>")
            if (label != null) append("<span>$label</span>")
            append("</label>\n")
        }
    }

    fun renderNumberInput(ir: NanoIR): String {
        val min = ir.props["min"]?.jsonPrimitive?.content
        val max = ir.props["max"]?.jsonPrimitive?.content
        val step = ir.props["step"]?.jsonPrimitive?.content ?: "1"
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: ""
        val bindingAttr = renderBindingAttribute(ir)
        val actionAttr = renderActionAttribute(ir)
        return buildString {
            append("<div class=\"nano-numberinput\">")
            append("<button class=\"nano-numberinput-decrement\">-</button>")
            append("<input type=\"number\" class=\"nano-numberinput-input\"")
            if (min != null) append(" min=\"$min\"")
            if (max != null) append(" max=\"$max\"")
            append(" step=\"$step\" placeholder=\"$placeholder\"$bindingAttr$actionAttr>")
            append("<button class=\"nano-numberinput-increment\">+</button>")
            append("</div>\n")
        }
    }

    // ============================================================================
    // P0: Feedback Components
    // ============================================================================

    fun renderModal(ir: NanoIR): String {
        val title = ir.props["title"]?.jsonPrimitive?.content
        val size = ir.props["size"]?.jsonPrimitive?.content ?: "md"
        val closable = ir.props["closable"]?.jsonPrimitive?.content?.toBoolean() ?: true
        val bindingAttr = renderBindingAttribute(ir)
        val actionAttr = renderActionAttribute(ir)
        return buildString {
            append("<div class=\"nano-modal size-$size\"$bindingAttr$actionAttr>\n")
            append("  <div class=\"nano-modal-backdrop\"></div>\n")
            append("  <div class=\"nano-modal-content\">\n")
            if (title != null) append("    <div class=\"nano-modal-header\"><h3>$title</h3>")
            if (closable) append("<button class=\"nano-modal-close\">×</button>")
            if (title != null) append("</div>\n")
            append("    <div class=\"nano-modal-body\">\n")
            ir.children?.forEach { append("      ${renderNode(it).replace("\n", "\n      ")}") }
            append("    </div>\n")
            append("  </div>\n")
            append("</div>\n")
        }
    }

    fun renderAlert(ir: NanoIR): String {
        val type = ir.props["type"]?.jsonPrimitive?.content ?: "info"
        val message = ir.props["message"]?.jsonPrimitive?.content
        val closable = ir.props["closable"]?.jsonPrimitive?.content?.toBoolean() ?: false
        val actionAttr = renderActionAttribute(ir)
        return buildString {
            append("<div class=\"nano-alert type-$type\"$actionAttr>\n")
            if (message != null) append("  <span class=\"nano-alert-message\">$message</span>\n")
            ir.children?.forEach { append("  ${renderNode(it).replace("\n", "\n  ")}") }
            if (closable) append("  <button class=\"nano-alert-close\">×</button>\n")
            append("</div>\n")
        }
    }

    fun renderProgress(ir: NanoIR): String {
        val valueStr = ir.props["value"]?.jsonPrimitive?.content
        val maxStr = ir.props["max"]?.jsonPrimitive?.content
        val value = valueStr?.toFloatOrNull() ?: 0f
        val max = maxStr?.toFloatOrNull() ?: 100f
        val showText = ir.props["showText"]?.jsonPrimitive?.content?.toBoolean() ?: true
        val status = ir.props["status"]?.jsonPrimitive?.content ?: "normal"
        val isBinding = (valueStr != null && valueStr.toFloatOrNull() == null) ||
                       (maxStr != null && maxStr.toFloatOrNull() == null)
        val percentage = if (max > 0f) ((value / max) * 100).toInt().coerceIn(0, 100) else 0
        return buildString {
            append("<div class=\"nano-progress status-$status\" data-value=\"${valueStr ?: "0"}\" data-max=\"${maxStr ?: "100"}\">\n")
            append("  <div class=\"nano-progress-bar\" style=\"width: $percentage%\"></div>\n")
            if (showText) {
                val displayText = if (isBinding) "${valueStr ?: "0"} / ${maxStr ?: "100"}" else "$percentage%"
                append("  <span class=\"nano-progress-text\">$displayText</span>\n")
            }
            append("</div>\n")
        }
    }

    fun renderSpinner(ir: NanoIR): String {
        val size = ir.props["size"]?.jsonPrimitive?.content ?: "md"
        val text = ir.props["text"]?.jsonPrimitive?.content
        return buildString {
            append("<div class=\"nano-spinner size-$size\">\n")
            append("  <div class=\"nano-spinner-circle\"></div>\n")
            if (text != null) append("  <span class=\"nano-spinner-text\">$text</span>\n")
            append("</div>\n")
        }
    }

    // ============================================================================
    // Tier 1-3: GenUI Components
    // ============================================================================

    fun renderSplitView(ir: NanoIR): String {
        val ratio = ir.props["ratio"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0.5f
        val leftWidth = (ratio * 100).toInt()
        val rightWidth = 100 - leftWidth
        return buildString {
            append("<div class=\"nano-splitview\"")
            append(renderFlexStyleAttribute(ir))
            append(">\n")
            append("  <div class=\"nano-splitview-left\" style=\"width: ${leftWidth}%\">\n")
            if (ir.children?.isNotEmpty() == true) append("    ${renderNode(ir.children[0]).replace("\n", "\n    ")}")
            append("  </div>\n")
            append("  <div class=\"nano-splitview-right\" style=\"width: ${rightWidth}%\">\n")
            if (ir.children?.size ?: 0 > 1) append("    ${renderNode(ir.children!![1]).replace("\n", "\n    ")}")
            append("  </div>\n")
            append("</div>\n")
        }
    }

    fun renderGenCanvas(ir: NanoIR): String {
        val bindingAttr = renderBindingAttribute(ir)
        return buildString {
            append("<div class=\"nano-gencanvas\"")
            append(renderFlexStyleAttribute(ir))
            append(bindingAttr)
            append(">")
            append("<!-- Canvas/preview will be rendered by client-side renderer -->")
            append("</div>\n")
        }
    }

    fun renderSmartTextField(ir: NanoIR): String {
        val label = ir.props["label"]?.jsonPrimitive?.content
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: ""
        val validation = ir.props["validation"]?.jsonPrimitive?.content
        val bindingAttr = renderBindingAttribute(ir)
        return buildString {
            if (label != null) append("<label class=\"nano-smarttextfield-label\">$label</label>\n")
            append("<input type=\"text\" class=\"nano-smarttextfield\" placeholder=\"$placeholder\"")
            if (validation != null) append(" pattern=\"$validation\"")
            append("$bindingAttr>\n")
        }
    }

    fun renderSlider(ir: NanoIR): String {
        val label = ir.props["label"]?.jsonPrimitive?.content
        val min = ir.props["min"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
        val max = ir.props["max"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 100f
        val step = ir.props["step"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1f
        val bindingAttr = renderBindingAttribute(ir)
        val actionAttr = renderActionAttribute(ir)
        
        // Get the binding expression to show the current value
        val binding = ir.bindings?.get("bind")
        val valueExpression = binding?.expression
        
        return buildString {
            append("<div class=\"nano-slider-container\">\n")
            
            // Render label and value in a row if label exists
            if (label != null || valueExpression != null) {
                append("  <div class=\"nano-slider-header\">\n")
                if (label != null) {
                    append("    <label class=\"nano-slider-label\">$label</label>\n")
                }
                if (valueExpression != null) {
                    append("    <span class=\"nano-slider-value\" data-bind=\"$valueExpression\">")
                    append("${min.toInt()}")  // Default value
                    append("</span>\n")
                }
                append("  </div>\n")
            }
            
            append("  <input type=\"range\" class=\"nano-slider\" min=\"$min\" max=\"$max\" step=\"$step\"$bindingAttr$actionAttr>\n")
            append("</div>\n")
        }
    }

    fun renderDateRangePicker(ir: NanoIR): String {
        val bindingAttr = renderBindingAttribute(ir)
        val actionAttr = renderActionAttribute(ir)
        return "<div class=\"nano-daterangepicker\"$bindingAttr$actionAttr><input type=\"date\" class=\"nano-daterangepicker-start\"><input type=\"date\" class=\"nano-daterangepicker-end\"></div>\n"
    }

    fun renderDataChart(ir: NanoIR): String {
        val type = ir.props["type"]?.jsonPrimitive?.content ?: "line"
        val data = ir.props["data"]?.jsonPrimitive?.content
        val dataAttr = data?.let { " data-data=\"${escapeHtmlAttr(it)}\"" } ?: ""
        return "<div class=\"nano-datachart type-$type\"$dataAttr><!-- Chart will be rendered by client-side library --></div>\n"
    }

    fun renderDataTable(ir: NanoIR): String {
        val columns = ir.props["columns"]?.jsonPrimitive?.content
        val data = ir.props["data"]?.jsonPrimitive?.content
        val actionAttr = renderActionAttribute(ir)
        val columnsAttr = columns?.let { " data-columns=\"${escapeHtmlAttr(it)}\"" } ?: ""
        val dataAttr = data?.let { " data-data=\"${escapeHtmlAttr(it)}\"" } ?: ""
        return buildString {
            append("<table class=\"nano-datatable\"$columnsAttr$dataAttr$actionAttr>\n")
            append("  <thead><tr><!-- Columns will be populated dynamically --></tr></thead>\n")
            append("  <tbody><!-- Rows will be populated dynamically --></tbody>\n")
            append("</table>\n")
        }
    }

    // ============================================================================
    // Action & Binding Helpers
    // ============================================================================

    /**
     * Render data-action attribute for components with actions
     *
     * Example output: data-action='{"on_click":{"type":"StateMutation","payload":{...}}}'
     */
    private fun renderActionAttribute(ir: NanoIR): String {
        val actions = ir.actions ?: return ""
        if (actions.isEmpty()) return ""

        val actionsJson = buildString {
            append("{")
            actions.entries.forEachIndexed { index, (event, action) ->
                if (index > 0) append(",")
                append("\"$event\":{")
                append("\"type\":\"${action.type}\"")
                action.payload?.let { payload ->
                    append(",\"payload\":{")
                    payload.entries.forEachIndexed { pIndex, (key, value) ->
                        if (pIndex > 0) append(",")
                        append("\"$key\":$value")
                    }
                    append("}")
                }
                append("}")
            }
            append("}")
        }

        return " data-actions='$actionsJson'"
    }

    /**
     * Render data-binding attribute for bound components
     *
     * Example output: data-bindings='{"value":{"mode":"twoWay","expression":"state.new_task"}}'
     */
    private fun renderBindingAttribute(ir: NanoIR): String {
        val bindings = ir.bindings ?: return ""
        if (bindings.isEmpty()) return ""

        val bindingsJson = buildString {
            append("{")
            bindings.entries.forEachIndexed { index, (prop, binding) ->
                if (index > 0) append(",")
                append("\"$prop\":{")
                append("\"mode\":\"${binding.mode}\",")
                append("\"expression\":\"${binding.expression}\"")
                append("}")
            }
            append("}")
        }

        return " data-bindings='$bindingsJson'"
    }

    private fun generateCss(): String = """
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }

        .nano-component { width: 100%; }

        .nano-vstack { display: flex; flex-direction: column; width: 100%; }
        .nano-hstack { display: flex; flex-direction: row; align-items: center; width: 100%; }
        .nano-hstack.wrap { flex-wrap: wrap; }

        /* HStack child flex support */
        .nano-hstack > .flex-1 { flex: 1; }
        .nano-hstack > .flex-2 { flex: 2; }
        .nano-hstack > .flex-3 { flex: 3; }
        .nano-hstack > .flex-auto { flex: 1 1 auto; }
        .nano-hstack > .flex-none { flex: none; }

        /* VStack children should take full width by default */
        .nano-vstack > * { width: 100%; }

        /* But in HStack, children should NOT take full width - let them size naturally */
        /* Only VStack children should flex, other elements (Text, Button) should size to content */
        .nano-hstack > .nano-vstack { flex: 1 1 0; min-width: 0; }
        .nano-hstack > .nano-text { flex: 0 0 auto; width: auto; }
        .nano-hstack > .nano-button { flex: 0 0 auto; width: auto; }
        .nano-hstack > .nano-input { flex: 1 1 0; min-width: 0; }

        .spacing-xs { gap: 4px; }
        .spacing-sm { gap: 8px; }
        .spacing-md { gap: 16px; }
        .spacing-lg { gap: 24px; }
        .spacing-xl { gap: 32px; }

        .align-start { align-items: flex-start; }
        .align-center { align-items: center; }
        .align-end { align-items: flex-end; }
        .align-stretch { align-items: stretch; }

        /* Prevent flex children from stretching height in HStack when align is not stretch */
        .nano-hstack.align-start > div,
        .nano-hstack.align-center > div,
        .nano-hstack.align-end > div {
            align-self: inherit;
            height: fit-content;
        }

        .justify-start { justify-content: flex-start; }
        .justify-center { justify-content: center; }
        .justify-end { justify-content: flex-end; }
        .justify-between { justify-content: space-between; }
        .justify-around { justify-content: space-around; }
        .justify-evenly { justify-content: space-evenly; }

        .nano-card {
            background: white;
            border-radius: 8px;
            overflow: hidden;
        }
        .padding-xs { padding: 4px; }
        .padding-sm { padding: 8px; }
        .padding-md { padding: 16px; }
        .padding-lg { padding: 24px; }

        .shadow-none { box-shadow: none; }
        .shadow-sm { box-shadow: 0 1px 2px rgba(0,0,0,0.1); }
        .shadow-md { box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
        .shadow-lg { box-shadow: 0 10px 15px rgba(0,0,0,0.1); }

        .nano-text { margin: 0; }
        .style-h1 { font-size: 2rem; font-weight: bold; }
        .style-h2 { font-size: 1.5rem; font-weight: bold; }
        .style-h3 { font-size: 1.25rem; font-weight: bold; }
        .style-h4 { font-size: 1rem; font-weight: bold; }
        .style-body { font-size: 1rem; }
        .style-caption { font-size: 0.875rem; color: #666; }

        .nano-button {
            padding: 8px 16px;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 1rem;
        }
        .intent-primary { background: #6200EE; color: white; }
        .intent-secondary { background: #03DAC6; color: black; }
        .intent-default { background: #E0E0E0; color: black; }
        .intent-error, .intent-danger { background: #B00020; color: white; }

        .nano-image { max-width: 100%; height: auto; display: block; }
        .radius-sm { border-radius: 4px; }
        .radius-md { border-radius: 8px; }
        .radius-lg { border-radius: 16px; }
        .radius-full { border-radius: 9999px; }

        .nano-badge {
            display: inline-block;
            padding: 2px 8px;
            border-radius: 12px;
            font-size: 0.75rem;
            font-weight: 500;
        }
        .color-green { background: #C8E6C9; color: #2E7D32; }
        .color-red { background: #FFCDD2; color: #C62828; }
        .color-blue { background: #BBDEFB; color: #1565C0; }
        .color-default { background: #E0E0E0; color: #424242; }

        .nano-input {
            padding: 8px 12px;
            border: 1px solid #E0E0E0;
            border-radius: 4px;
            font-size: 1rem;
            width: 100%;
            min-width: 0; /* Allow input to shrink in flex containers */
        }

        .nano-textarea {
            padding: 8px 12px;
            border: 1px solid #E0E0E0;
            border-radius: 4px;
            font-size: 1rem;
            width: 100%;
            resize: vertical;
            font-family: inherit;
        }

        .nano-select {
            padding: 8px 12px;
            border: 1px solid #E0E0E0;
            border-radius: 4px;
            font-size: 1rem;
            width: 100%;
            background: white;
        }

        .nano-form {
            display: flex;
            flex-direction: column;
            gap: 16px;
        }

        .nano-checkbox-wrapper {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            cursor: pointer;
        }

        .nano-checkbox {
            width: 16px;
            height: 16px;
            accent-color: #1976D2;
            cursor: pointer;
        }

        .nano-divider { border: none; border-top: 1px solid #E0E0E0; margin: 16px 0; }
        
        .nano-slider-container {
            width: 100%;
        }
        
        .nano-slider-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 8px;
        }
        
        .nano-slider-label {
            font-size: 0.875rem;
            color: #424242;
            font-weight: 500;
        }
        
        .nano-slider-value {
            font-size: 0.875rem;
            color: #1976D2;
            font-weight: 600;
        }
        
        .nano-slider {
            width: 100%;
            height: 6px;
            border-radius: 3px;
            background: #E0E0E0;
            outline: none;
            -webkit-appearance: none;
            appearance: none;
        }
        
        .nano-slider::-webkit-slider-thumb {
            -webkit-appearance: none;
            appearance: none;
            width: 18px;
            height: 18px;
            border-radius: 50%;
            background: #1976D2;
            cursor: pointer;
            transition: all 0.2s ease;
        }
        
        .nano-slider::-webkit-slider-thumb:hover {
            background: #1565C0;
            transform: scale(1.1);
        }
        
        .nano-slider::-moz-range-thumb {
            width: 18px;
            height: 18px;
            border-radius: 50%;
            background: #1976D2;
            cursor: pointer;
            border: none;
            transition: all 0.2s ease;
        }
        
        .nano-slider::-moz-range-thumb:hover {
            background: #1565C0;
            transform: scale(1.1);
        }
        
        .nano-slider::-webkit-slider-runnable-track {
            width: 100%;
            height: 6px;
            background: linear-gradient(to right, #1976D2 0%, #1976D2 var(--value, 0%), #E0E0E0 var(--value, 0%), #E0E0E0 100%);
            border-radius: 3px;
        }
        
        .nano-slider::-moz-range-track {
            width: 100%;
            height: 6px;
            background: #E0E0E0;
            border-radius: 3px;
        }
    """.trimIndent()
}

