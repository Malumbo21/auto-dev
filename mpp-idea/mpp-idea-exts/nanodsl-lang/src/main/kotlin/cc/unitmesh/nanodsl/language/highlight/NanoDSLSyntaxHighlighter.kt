package cc.unitmesh.nanodsl.language.highlight

import cc.unitmesh.nanodsl.language.lexer.NanoDSLLexerAdapter
import cc.unitmesh.nanodsl.language.psi.NanoDSLTypes
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

class NanoDSLSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = NanoDSLLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> {
        return pack(ATTRIBUTES[tokenType])
    }

    companion object {
        private val ATTRIBUTES: MutableMap<IElementType, TextAttributesKey> = HashMap()

        // Custom TextAttributesKey for more colorful highlighting
        // Keywords - bold purple/magenta for control flow
        val KEYWORD: TextAttributesKey = createTextAttributesKey("NANODSL_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)

        // Event handlers - cyan/teal for event-driven elements
        val EVENT_HANDLER: TextAttributesKey = createTextAttributesKey("NANODSL_EVENT_HANDLER", DefaultLanguageHighlighterColors.FUNCTION_CALL)

        // Request properties - orange for API-related properties
        val REQUEST_PROP: TextAttributesKey = createTextAttributesKey("NANODSL_REQUEST_PROP", DefaultLanguageHighlighterColors.METADATA)

        // Layout components - blue for structural elements
        val LAYOUT_COMPONENT: TextAttributesKey = createTextAttributesKey("NANODSL_LAYOUT_COMPONENT", DefaultLanguageHighlighterColors.CLASS_NAME)

        // Display components - green for visual elements
        val DISPLAY_COMPONENT: TextAttributesKey = createTextAttributesKey("NANODSL_DISPLAY_COMPONENT", DefaultLanguageHighlighterColors.INTERFACE_NAME)

        // Input components - yellow/gold for interactive elements
        val INPUT_COMPONENT: TextAttributesKey = createTextAttributesKey("NANODSL_INPUT_COMPONENT", DefaultLanguageHighlighterColors.INSTANCE_FIELD)

        // Actions - red/coral for action triggers
        val ACTION: TextAttributesKey = createTextAttributesKey("NANODSL_ACTION", DefaultLanguageHighlighterColors.STATIC_METHOD)

        // HTTP methods - bold constant style
        val HTTP_METHOD: TextAttributesKey = createTextAttributesKey("NANODSL_HTTP_METHOD", DefaultLanguageHighlighterColors.CONSTANT)

        // Types - italic for type annotations
        val TYPE: TextAttributesKey = createTextAttributesKey("NANODSL_TYPE", DefaultLanguageHighlighterColors.CLASS_REFERENCE)

        // Operators - distinct operator style
        val OPERATOR: TextAttributesKey = createTextAttributesKey("NANODSL_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)

        // Binding operators - special style for data binding
        val BINDING: TextAttributesKey = createTextAttributesKey("NANODSL_BINDING", DefaultLanguageHighlighterColors.LABEL)

        // String literals
        val STRING: TextAttributesKey = createTextAttributesKey("NANODSL_STRING", DefaultLanguageHighlighterColors.STRING)

        // Number literals
        val NUMBER: TextAttributesKey = createTextAttributesKey("NANODSL_NUMBER", DefaultLanguageHighlighterColors.NUMBER)

        // Boolean literals
        val BOOLEAN: TextAttributesKey = createTextAttributesKey("NANODSL_BOOLEAN", DefaultLanguageHighlighterColors.KEYWORD)

        // Comments
        val COMMENT: TextAttributesKey = createTextAttributesKey("NANODSL_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)

        // Identifiers
        val IDENTIFIER: TextAttributesKey = createTextAttributesKey("NANODSL_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)

        // Braces and brackets
        val BRACES: TextAttributesKey = createTextAttributesKey("NANODSL_BRACES", DefaultLanguageHighlighterColors.BRACES)
        val BRACKETS: TextAttributesKey = createTextAttributesKey("NANODSL_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)
        val PARENTHESES: TextAttributesKey = createTextAttributesKey("NANODSL_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)

        // Punctuation
        val COMMA: TextAttributesKey = createTextAttributesKey("NANODSL_COMMA", DefaultLanguageHighlighterColors.COMMA)
        val DOT: TextAttributesKey = createTextAttributesKey("NANODSL_DOT", DefaultLanguageHighlighterColors.DOT)

        // Bad character
        val BAD_CHARACTER: TextAttributesKey = createTextAttributesKey("NANODSL_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

        // Keywords
        private val KEYWORDS: TokenSet = TokenSet.create(
            NanoDSLTypes.COMPONENT,
            NanoDSLTypes.STATE,
            NanoDSLTypes.REQUEST,
            NanoDSLTypes.IF,
            NanoDSLTypes.FOR,
            NanoDSLTypes.IN,
            NanoDSLTypes.CONTENT,
        )

        // Event handlers
        private val EVENT_HANDLERS: TokenSet = TokenSet.create(
            NanoDSLTypes.ON_CLICK,
            NanoDSLTypes.ON_SUCCESS,
            NanoDSLTypes.ON_ERROR,
            NanoDSLTypes.ON_CLOSE,
            NanoDSLTypes.ON_ROW_CLICK,
        )

        // Request properties
        private val REQUEST_PROPS: TokenSet = TokenSet.create(
            NanoDSLTypes.URL,
            NanoDSLTypes.METHOD,
            NanoDSLTypes.BODY,
            NanoDSLTypes.HEADERS,
            NanoDSLTypes.TO,
            NanoDSLTypes.PARAMS,
            NanoDSLTypes.QUERY,
            NanoDSLTypes.REPLACE,
        )

        // Layout components
        private val LAYOUT_COMPONENTS: TokenSet = TokenSet.create(
            NanoDSLTypes.VSTACK,
            NanoDSLTypes.HSTACK,
            NanoDSLTypes.CARD,
            NanoDSLTypes.SPLITVIEW,
            NanoDSLTypes.GENCANVAS,
            NanoDSLTypes.FORM,
            NanoDSLTypes.MODAL,
        )

        // Display components
        private val DISPLAY_COMPONENTS: TokenSet = TokenSet.create(
            NanoDSLTypes.TEXT,
            NanoDSLTypes.IMAGE,
            NanoDSLTypes.BADGE,
            NanoDSLTypes.DIVIDER,
            NanoDSLTypes.ALERT,
            NanoDSLTypes.PROGRESS,
            NanoDSLTypes.SPINNER,
            NanoDSLTypes.DATACHART,
            NanoDSLTypes.DATATABLE,
        )

        // Input components
        private val INPUT_COMPONENTS: TokenSet = TokenSet.create(
            NanoDSLTypes.BUTTON,
            NanoDSLTypes.INPUT,
            NanoDSLTypes.TEXTAREA,
            NanoDSLTypes.SELECT,
            NanoDSLTypes.CHECKBOX,
            NanoDSLTypes.RADIO,
            NanoDSLTypes.RADIOGROUP,
            NanoDSLTypes.SWITCH,
            NanoDSLTypes.NUMBERINPUT,
            NanoDSLTypes.SMARTTEXTFIELD,
            NanoDSLTypes.SLIDER,
            NanoDSLTypes.DATEPICKER,
            NanoDSLTypes.DATERANGEPICKER,
        )

        // Actions
        private val ACTIONS: TokenSet = TokenSet.create(
            NanoDSLTypes.NAVIGATE,
            NanoDSLTypes.FETCH,
            NanoDSLTypes.SHOWTOAST,
            NanoDSLTypes.STATEMUTATION,
        )

        // HTTP methods
        private val HTTP_METHODS: TokenSet = TokenSet.create(
            NanoDSLTypes.GET,
            NanoDSLTypes.POST,
            NanoDSLTypes.PUT,
            NanoDSLTypes.PATCH,
            NanoDSLTypes.DELETE,
        )

        // Types
        private val TYPES: TokenSet = TokenSet.create(
            NanoDSLTypes.INT,
            NanoDSLTypes.FLOAT,
            NanoDSLTypes.STRING_TYPE,
            NanoDSLTypes.BOOL,
        )

        // Binding operators
        private val BINDING_OPERATORS: TokenSet = TokenSet.create(
            NanoDSLTypes.BIND_READ,
            NanoDSLTypes.BIND_WRITE,
        )

        // Compound assignment operators
        private val COMPOUND_OPERATORS: TokenSet = TokenSet.create(
            NanoDSLTypes.PLUS_EQUALS,
            NanoDSLTypes.MINUS_EQUALS,
            NanoDSLTypes.TIMES_EQUALS,
            NanoDSLTypes.DIV_EQUALS,
        )

        init {
            // Map token sets to custom TextAttributesKey
            fillMap(ATTRIBUTES, KEYWORDS, KEYWORD)
            fillMap(ATTRIBUTES, EVENT_HANDLERS, EVENT_HANDLER)
            fillMap(ATTRIBUTES, REQUEST_PROPS, REQUEST_PROP)
            fillMap(ATTRIBUTES, LAYOUT_COMPONENTS, LAYOUT_COMPONENT)
            fillMap(ATTRIBUTES, DISPLAY_COMPONENTS, DISPLAY_COMPONENT)
            fillMap(ATTRIBUTES, INPUT_COMPONENTS, INPUT_COMPONENT)
            fillMap(ATTRIBUTES, ACTIONS, ACTION)
            fillMap(ATTRIBUTES, HTTP_METHODS, HTTP_METHOD)
            fillMap(ATTRIBUTES, TYPES, TYPE)
            fillMap(ATTRIBUTES, BINDING_OPERATORS, BINDING)
            fillMap(ATTRIBUTES, COMPOUND_OPERATORS, OPERATOR)

            // Literals
            ATTRIBUTES[NanoDSLTypes.STRING] = STRING
            ATTRIBUTES[NanoDSLTypes.NUMBER] = NUMBER
            ATTRIBUTES[NanoDSLTypes.BOOLEAN] = BOOLEAN

            // Comments
            ATTRIBUTES[NanoDSLTypes.COMMENT] = COMMENT

            // Identifiers
            ATTRIBUTES[NanoDSLTypes.IDENTIFIER] = IDENTIFIER

            // Punctuation
            ATTRIBUTES[NanoDSLTypes.COLON] = OPERATOR
            ATTRIBUTES[NanoDSLTypes.COMMA] = COMMA
            ATTRIBUTES[NanoDSLTypes.DOT] = DOT
            ATTRIBUTES[NanoDSLTypes.EQUALS] = OPERATOR

            // Brackets
            ATTRIBUTES[NanoDSLTypes.LPAREN] = PARENTHESES
            ATTRIBUTES[NanoDSLTypes.RPAREN] = PARENTHESES
            ATTRIBUTES[NanoDSLTypes.LBRACE] = BRACES
            ATTRIBUTES[NanoDSLTypes.RBRACE] = BRACES
            ATTRIBUTES[NanoDSLTypes.LBRACKET] = BRACKETS
            ATTRIBUTES[NanoDSLTypes.RBRACKET] = BRACKETS
        }
    }
}

