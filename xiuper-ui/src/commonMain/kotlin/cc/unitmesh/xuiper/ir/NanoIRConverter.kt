package cc.unitmesh.xuiper.ir

import cc.unitmesh.xuiper.action.BodyField
import cc.unitmesh.xuiper.action.NanoAction
import cc.unitmesh.xuiper.ast.Binding
import cc.unitmesh.xuiper.ast.NanoNode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Converts NanoNode AST to NanoIR (JSON Intermediate Representation)
 */
object NanoIRConverter {

    /**
     * Convert a NanoNode to NanoIR
     */
    fun convert(node: NanoNode): NanoIR {
        return when (node) {
            is NanoNode.Component -> convertComponent(node)
            is NanoNode.VStack -> convertVStack(node)
            is NanoNode.HStack -> convertHStack(node)
            is NanoNode.Card -> convertCard(node)
            is NanoNode.Text -> convertText(node)
            is NanoNode.Image -> convertImage(node)
            is NanoNode.Badge -> convertBadge(node)
            is NanoNode.Icon -> convertIcon(node)
            is NanoNode.Button -> convertButton(node)
            is NanoNode.Input -> convertInput(node)
            is NanoNode.Checkbox -> convertCheckbox(node)
            is NanoNode.Conditional -> convertConditional(node)
            is NanoNode.ForLoop -> convertForLoop(node)
            is NanoNode.HttpRequest -> convertHttpRequest(node)
            is NanoNode.Form -> convertForm(node)
            is NanoNode.Select -> convertSelect(node)
            is NanoNode.TextArea -> convertTextArea(node)
            // Tier 1: GenUI Foundation
            is NanoNode.SplitView -> convertSplitView(node)
            // Tier 2: Structured Input
            is NanoNode.SmartTextField -> convertSmartTextField(node)
            is NanoNode.Slider -> convertSlider(node)
            is NanoNode.DateRangePicker -> convertDateRangePicker(node)
            // Tier 3: Data Artifacts
            is NanoNode.DataChart -> convertDataChart(node)
            is NanoNode.DataTable -> convertDataTable(node)
            // P0: Core Form Input Components
            is NanoNode.DatePicker -> convertDatePicker(node)
            is NanoNode.Radio -> convertRadio(node)
            is NanoNode.RadioGroup -> convertRadioGroup(node)
            is NanoNode.Switch -> convertSwitch(node)
            is NanoNode.NumberInput -> convertNumberInput(node)
            // P0: Feedback Components
            is NanoNode.Modal -> convertModal(node)
            is NanoNode.Alert -> convertAlert(node)
            is NanoNode.Progress -> convertProgress(node)
            is NanoNode.Spinner -> convertSpinner(node)
            NanoNode.Divider -> NanoIR(type = "Divider")
        }
    }

    private fun convertComponent(node: NanoNode.Component): NanoIR {
        val props = mutableMapOf<String, JsonElement>(
            "name" to JsonPrimitive(node.name)
        )

        val state = node.state?.let { stateBlock ->
            val vars = stateBlock.variables.associate { v ->
                v.name to NanoStateVarIR(
                    type = v.type,
                    defaultValue = v.defaultValue?.let { JsonPrimitive(it) }
                )
            }
            NanoStateIR(vars)
        }

        return NanoIR(
            type = "Component",
            props = props,
            children = node.children.map { convert(it) },
            state = state
        )
    }

    private fun convertVStack(node: NanoNode.VStack): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.spacing?.let { props["spacing"] = JsonPrimitive(it) }
        node.align?.let { props["align"] = JsonPrimitive(it) }

        return NanoIR(
            type = "VStack",
            props = props,
            children = node.children.map { convert(it) }
        )
    }

    private fun convertHStack(node: NanoNode.HStack): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.spacing?.let { props["spacing"] = JsonPrimitive(it) }
        node.align?.let { props["align"] = JsonPrimitive(it) }
        node.justify?.let { props["justify"] = JsonPrimitive(it) }

        return NanoIR(
            type = "HStack",
            props = props,
            children = node.children.map { convert(it) }
        )
    }

    private fun convertCard(node: NanoNode.Card): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.padding?.let { props["padding"] = JsonPrimitive(it) }
        node.shadow?.let { props["shadow"] = JsonPrimitive(it) }

        return NanoIR(
            type = "Card",
            props = props,
            children = node.children.map { convert(it) }
        )
    }

    private fun convertText(node: NanoNode.Text): NanoIR {
        val props = mutableMapOf<String, JsonElement>(
            "content" to JsonPrimitive(node.content)
        )
        node.style?.let { props["style"] = JsonPrimitive(it) }

        val bindings = node.binding?.let {
            mapOf("content" to convertBinding(it))
        }

        return NanoIR(type = "Text", props = props, bindings = bindings)
    }

    private fun convertImage(node: NanoNode.Image): NanoIR {
        val props = mutableMapOf<String, JsonElement>(
            "src" to JsonPrimitive(node.src)
        )
        node.aspect?.let { props["aspect"] = JsonPrimitive(it) }
        node.radius?.let { props["radius"] = JsonPrimitive(it) }
        node.width?.let { props["width"] = JsonPrimitive(it) }

        return NanoIR(type = "Image", props = props)
    }

    private fun convertBadge(node: NanoNode.Badge): NanoIR {
        val props = mutableMapOf<String, JsonElement>(
            "text" to JsonPrimitive(node.text)
        )
        node.color?.let { props["color"] = JsonPrimitive(it) }

        return NanoIR(type = "Badge", props = props)
    }

    private fun convertIcon(node: NanoNode.Icon): NanoIR {
        val props = mutableMapOf<String, JsonElement>(
            "name" to JsonPrimitive(node.name)
        )
        node.size?.let { props["size"] = JsonPrimitive(it) }
        node.color?.let { props["color"] = JsonPrimitive(it) }

        return NanoIR(type = "Icon", props = props)
    }

    private fun convertButton(node: NanoNode.Button): NanoIR {
        val props = mutableMapOf<String, JsonElement>(
            "label" to JsonPrimitive(node.label)
        )
        node.intent?.let { props["intent"] = JsonPrimitive(it) }
        node.icon?.let { props["icon"] = JsonPrimitive(it) }
        node.disabledIf?.let { props["disabled_if"] = JsonPrimitive(it) }

        val actions = node.onClick?.let {
            mapOf("onClick" to convertAction(it))
        }

        return NanoIR(type = "Button", props = props, actions = actions)
    }

    private fun convertInput(node: NanoNode.Input): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.placeholder?.let { props["placeholder"] = JsonPrimitive(it) }
        node.type?.let { props["type"] = JsonPrimitive(it) }

        val bindings = node.value?.let {
            mapOf("value" to convertBinding(it))
        }

        return NanoIR(type = "Input", props = props, bindings = bindings)
    }

    private fun convertCheckbox(node: NanoNode.Checkbox): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.label?.let { props["label"] = JsonPrimitive(it) }

        val bindings = node.checked?.let {
            mapOf("checked" to convertBinding(it))
        }

        val actions = node.onChange?.let {
            mapOf("onChange" to convertAction(it))
        }

        return NanoIR(type = "Checkbox", props = props, bindings = bindings, actions = actions)
    }

    private fun convertConditional(node: NanoNode.Conditional): NanoIR {
        val children = node.thenBranch.map { convert(it) }

        return NanoIR(
            type = "Conditional",
            condition = node.condition,
            children = children
        )
    }

    private fun convertForLoop(node: NanoNode.ForLoop): NanoIR {
        val children = node.body.map { convert(it) }

        return NanoIR(
            type = "ForLoop",
            loop = NanoLoopIR(
                variable = node.variable,
                iterable = node.iterable
            ),
            children = children
        )
    }

    private fun convertHttpRequest(node: NanoNode.HttpRequest): NanoIR {
        val props = mutableMapOf<String, JsonElement>(
            "name" to JsonPrimitive(node.name),
            "url" to JsonPrimitive(node.url),
            "method" to JsonPrimitive(node.method.name),
            "contentType" to JsonPrimitive(node.contentType.mimeType)
        )

        // Convert body fields
        if (node.body.isNotEmpty()) {
            val bodyFields = node.body.associate { field ->
                field.name to convertBodyField(field.value)
            }
            props["body"] = JsonObject(bodyFields)
        }

        // Convert headers
        if (node.headers.isNotEmpty()) {
            props["headers"] = JsonObject(node.headers.mapValues { JsonPrimitive(it.value) })
        }

        // Convert params
        if (node.params.isNotEmpty()) {
            props["params"] = JsonObject(node.params.mapValues { JsonPrimitive(it.value) })
        }

        // Convert actions
        val actions = mutableMapOf<String, NanoActionIR>()
        node.onLoading?.let { actions["onLoading"] = convertAction(it) }
        node.onSuccess?.let { actions["onSuccess"] = convertAction(it) }
        node.onError?.let { actions["onError"] = convertAction(it) }

        // Convert bindings
        val bindings = mutableMapOf<String, NanoBindingIR>()
        node.loadingBinding?.let { bindings["loading"] = convertBinding(it) }
        node.responseBinding?.let { bindings["response"] = convertBinding(it) }
        node.errorBinding?.let { bindings["error"] = convertBinding(it) }

        return NanoIR(
            type = "HttpRequest",
            props = props,
            actions = actions.takeIf { it.isNotEmpty() },
            bindings = bindings.takeIf { it.isNotEmpty() }
        )
    }

    private fun convertBodyField(field: BodyField): JsonElement {
        return when (field) {
            is BodyField.Literal -> JsonPrimitive(field.value)
            is BodyField.StateBinding -> JsonObject(
                mapOf(
                    "type" to JsonPrimitive("binding"),
                    "path" to JsonPrimitive(field.path)
                )
            )
        }
    }

    private fun convertForm(node: NanoNode.Form): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.onSubmit?.let { props["onSubmit"] = JsonPrimitive(it) }

        val actions = node.onSubmitAction?.let {
            mapOf("onSubmit" to convertAction(it))
        }

        return NanoIR(
            type = "Form",
            props = props,
            children = node.children.map { convert(it) },
            actions = actions
        )
    }

    private fun convertSelect(node: NanoNode.Select): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.options?.let { props["options"] = JsonPrimitive(it) }
        node.placeholder?.let { props["placeholder"] = JsonPrimitive(it) }

        val bindings = node.value?.let {
            mapOf("value" to convertBinding(it))
        }

        return NanoIR(type = "Select", props = props, bindings = bindings)
    }

    private fun convertTextArea(node: NanoNode.TextArea): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.rows?.let { props["rows"] = JsonPrimitive(it) }
        node.placeholder?.let { props["placeholder"] = JsonPrimitive(it) }

        val bindings = node.value?.let {
            mapOf("value" to convertBinding(it))
        }

        return NanoIR(type = "TextArea", props = props, bindings = bindings)
    }

    private fun convertBinding(binding: Binding): NanoBindingIR {
        return when (binding) {
            is Binding.Subscribe -> NanoBindingIR(
                mode = "subscribe",
                expression = binding.expression
            )
            is Binding.TwoWay -> NanoBindingIR(
                mode = "twoWay",
                expression = binding.path
            )
            is Binding.Static -> NanoBindingIR(
                mode = "static",
                expression = binding.value
            )
        }
    }

    private fun convertAction(action: NanoAction): NanoActionIR {
        return when (action) {
            is NanoAction.StateMutation -> NanoActionIR(
                type = "stateMutation",
                payload = mapOf(
                    "path" to JsonPrimitive(action.path),
                    "operation" to JsonPrimitive(action.operation.name),
                    "value" to JsonPrimitive(action.value)
                )
            )
            is NanoAction.Navigate -> NanoActionIR(
                type = "navigate",
                payload = mapOf("to" to JsonPrimitive(action.to))
            )
            is NanoAction.Fetch -> {
                val payload = mutableMapOf<String, JsonElement>(
                    "url" to JsonPrimitive(action.url),
                    "method" to JsonPrimitive(action.method.name),
                    "contentType" to JsonPrimitive(action.contentType.mimeType)
                )

                // Add body fields
                action.body?.let { body ->
                    payload["body"] = JsonObject(body.mapValues { convertBodyField(it.value) })
                }

                // Add headers
                action.headers?.let { headers ->
                    payload["headers"] = JsonObject(headers.mapValues { JsonPrimitive(it.value) })
                }

                // Add params
                action.params?.let { params ->
                    payload["params"] = JsonObject(params.mapValues { JsonPrimitive(it.value) })
                }

                // Add state bindings
                action.loadingState?.let { payload["loadingState"] = JsonPrimitive(it) }
                action.responseBinding?.let { payload["responseBinding"] = JsonPrimitive(it) }
                action.errorBinding?.let { payload["errorBinding"] = JsonPrimitive(it) }

                NanoActionIR(type = "fetch", payload = payload)
            }
            is NanoAction.ShowToast -> NanoActionIR(
                type = "showToast",
                payload = mapOf("message" to JsonPrimitive(action.message))
            )
            is NanoAction.Sequence -> NanoActionIR(
                type = "sequence"
            )
        }
    }

    // ============ Tier 1: GenUI Foundation Components ============

    private fun convertSplitView(node: NanoNode.SplitView): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.ratio?.let { props["ratio"] = JsonPrimitive(it) }

        return NanoIR(
            type = "SplitView",
            props = props,
            children = node.children.map { convert(it) }
        )
    }

    // ============ Tier 2: Structured Input Components ============

    private fun convertSmartTextField(node: NanoNode.SmartTextField): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.name?.let { props["name"] = JsonPrimitive(it) }
        node.label?.let { props["label"] = JsonPrimitive(it) }
        node.validation?.let { props["validation"] = JsonPrimitive(it) }
        node.placeholder?.let { props["placeholder"] = JsonPrimitive(it) }

        val bindings = node.bind?.let {
            mapOf("bind" to convertBinding(it))
        }

        return NanoIR(type = "SmartTextField", props = props, bindings = bindings)
    }

    private fun convertSlider(node: NanoNode.Slider): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.name?.let { props["name"] = JsonPrimitive(it) }
        node.label?.let { props["label"] = JsonPrimitive(it) }
        node.min?.let { props["min"] = JsonPrimitive(it) }
        node.max?.let { props["max"] = JsonPrimitive(it) }
        node.step?.let { props["step"] = JsonPrimitive(it) }

        val bindings = node.bind?.let {
            mapOf("bind" to convertBinding(it))
        }

        val actions = node.onChange?.let {
            mapOf("onChange" to convertAction(it))
        }

        return NanoIR(type = "Slider", props = props, bindings = bindings, actions = actions)
    }

    private fun convertDateRangePicker(node: NanoNode.DateRangePicker): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.name?.let { props["name"] = JsonPrimitive(it) }

        val bindings = node.bind?.let {
            mapOf("bind" to convertBinding(it))
        }

        val actions = node.onChange?.let {
            mapOf("onChange" to convertAction(it))
        }

        return NanoIR(type = "DateRangePicker", props = props, bindings = bindings, actions = actions)
    }

    // ============ Tier 3: Data Artifacts ============

    private fun convertDataChart(node: NanoNode.DataChart): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.name?.let { props["name"] = JsonPrimitive(it) }
        node.type?.let { props["type"] = JsonPrimitive(it) }
        node.data?.let { props["data"] = JsonPrimitive(it) }
        node.xAxis?.let { props["x_axis"] = JsonPrimitive(it) }
        node.yAxis?.let { props["y_axis"] = JsonPrimitive(it) }
        node.color?.let { props["color"] = JsonPrimitive(it) }

        return NanoIR(type = "DataChart", props = props)
    }

    private fun convertDataTable(node: NanoNode.DataTable): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.name?.let { props["name"] = JsonPrimitive(it) }
        node.columns?.let { props["columns"] = JsonPrimitive(it) }
        node.data?.let { props["data"] = JsonPrimitive(it) }

        val actions = node.onRowClick?.let {
            mapOf("onRowClick" to convertAction(it))
        }

        return NanoIR(type = "DataTable", props = props, actions = actions)
    }

    // ============ P0: Core Form Input Components ============

    private fun convertDatePicker(node: NanoNode.DatePicker): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.format?.let { props["format"] = JsonPrimitive(it) }
        node.minDate?.let { props["minDate"] = JsonPrimitive(it) }
        node.maxDate?.let { props["maxDate"] = JsonPrimitive(it) }
        node.placeholder?.let { props["placeholder"] = JsonPrimitive(it) }

        val bindings = node.value?.let {
            mapOf("value" to convertBinding(it))
        }

        val actions = node.onChange?.let {
            mapOf("onChange" to convertAction(it))
        }

        return NanoIR(type = "DatePicker", props = props, bindings = bindings, actions = actions)
    }

    private fun convertRadio(node: NanoNode.Radio): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.option?.let { props["option"] = JsonPrimitive(it) }
        node.label?.let { props["label"] = JsonPrimitive(it) }
        node.name?.let { props["name"] = JsonPrimitive(it) }

        val bindings = node.value?.let {
            mapOf("value" to convertBinding(it))
        }

        return NanoIR(type = "Radio", props = props, bindings = bindings)
    }

    private fun convertRadioGroup(node: NanoNode.RadioGroup): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.options?.let { props["options"] = JsonPrimitive(it) }
        node.name?.let { props["name"] = JsonPrimitive(it) }

        val bindings = node.value?.let {
            mapOf("value" to convertBinding(it))
        }

        return NanoIR(
            type = "RadioGroup",
            props = props,
            bindings = bindings,
            children = node.children.map { convert(it) }
        )
    }

    private fun convertSwitch(node: NanoNode.Switch): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.label?.let { props["label"] = JsonPrimitive(it) }
        node.size?.let { props["size"] = JsonPrimitive(it) }

        val bindings = node.checked?.let {
            mapOf("checked" to convertBinding(it))
        }

        val actions = node.onChange?.let {
            mapOf("onChange" to convertAction(it))
        }

        return NanoIR(type = "Switch", props = props, bindings = bindings, actions = actions)
    }

    private fun convertNumberInput(node: NanoNode.NumberInput): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.min?.let { props["min"] = JsonPrimitive(it) }
        node.max?.let { props["max"] = JsonPrimitive(it) }
        node.step?.let { props["step"] = JsonPrimitive(it) }
        node.precision?.let { props["precision"] = JsonPrimitive(it) }
        node.placeholder?.let { props["placeholder"] = JsonPrimitive(it) }

        val bindings = node.value?.let {
            mapOf("value" to convertBinding(it))
        }

        val actions = node.onChange?.let {
            mapOf("onChange" to convertAction(it))
        }

        return NanoIR(type = "NumberInput", props = props, bindings = bindings, actions = actions)
    }

    // ============ P0: Feedback Components ============

    private fun convertModal(node: NanoNode.Modal): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.title?.let { props["title"] = JsonPrimitive(it) }
        node.size?.let { props["size"] = JsonPrimitive(it) }
        node.closable?.let { props["closable"] = JsonPrimitive(it) }

        val bindings = node.open?.let {
            mapOf("open" to convertBinding(it))
        }

        val actions = node.onClose?.let {
            mapOf("onClose" to convertAction(it))
        }

        return NanoIR(
            type = "Modal",
            props = props,
            bindings = bindings,
            actions = actions,
            children = node.children.map { convert(it) }
        )
    }

    private fun convertAlert(node: NanoNode.Alert): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.type?.let { props["type"] = JsonPrimitive(it) }
        node.message?.let { props["message"] = JsonPrimitive(it) }
        node.closable?.let { props["closable"] = JsonPrimitive(it) }
        node.icon?.let { props["icon"] = JsonPrimitive(it) }

        val actions = node.onClose?.let {
            mapOf("onClose" to convertAction(it))
        }

        return NanoIR(
            type = "Alert",
            props = props,
            actions = actions,
            children = node.children.map { convert(it) }
        )
    }

    private fun convertProgress(node: NanoNode.Progress): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.value?.let { props["value"] = JsonPrimitive(it) }
        node.max?.let { props["max"] = JsonPrimitive(it) }
        node.showText?.let { props["showText"] = JsonPrimitive(it) }
        node.status?.let { props["status"] = JsonPrimitive(it) }

        return NanoIR(type = "Progress", props = props)
    }

    private fun convertSpinner(node: NanoNode.Spinner): NanoIR {
        val props = mutableMapOf<String, JsonElement>()
        node.size?.let { props["size"] = JsonPrimitive(it) }
        node.color?.let { props["color"] = JsonPrimitive(it) }
        node.text?.let { props["text"] = JsonPrimitive(it) }

        return NanoIR(type = "Spinner", props = props)
    }
}

