package cc.unitmesh.viewer.web.webedit

/**
 * JavaScript code to inject into WebView for DOM selection and communication
 * 
 * This script provides:
 * - Selection mode with element highlighting
 * - DOM tree extraction
 * - Bidirectional communication with Kotlin via kmpJsBridge
 */
fun getWebEditBridgeScript(): String = """
(function() {
    // Prevent multiple injections
    if (window.webEditBridge) return;
    
    // WebEdit Bridge object
    window.webEditBridge = {
        selectionMode: false,
        highlightedElement: null,
        selectedElement: null,
        
        // Enable/disable selection mode
        setSelectionMode: function(enabled) {
            this.selectionMode = enabled;
            if (!enabled) {
                this.clearHighlights();
            }
            console.log('[WebEditBridge] Selection mode:', enabled);
        },
        
        // Highlight a specific element by selector
        highlightElement: function(selector) {
            this.clearHighlights();
            try {
                const el = document.querySelector(selector);
                if (el) {
                    this.highlightedElement = el;
                    el.style.outline = '2px solid #2196F3';
                    el.style.outlineOffset = '2px';
                }
            } catch(e) {
                console.error('[WebEditBridge] highlightElement error:', e);
            }
        },
        
        // Clear all highlights
        clearHighlights: function() {
            if (this.highlightedElement) {
                this.highlightedElement.style.outline = '';
                this.highlightedElement.style.outlineOffset = '';
                this.highlightedElement = null;
            }
            if (this.selectedElement) {
                this.selectedElement.style.outline = '';
                this.selectedElement.style.outlineOffset = '';
            }
        },
        
        // Scroll to element
        scrollToElement: function(selector) {
            try {
                const el = document.querySelector(selector);
                if (el) {
                    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
            } catch(e) {
                console.error('[WebEditBridge] scrollToElement error:', e);
            }
        },
        
        // Get unique selector for element
        getSelector: function(el) {
            if (el.id) return '#' + el.id;
            if (el === document.body) return 'body';
            if (el === document.documentElement) return 'html';
            
            const path = [];
            while (el && el.nodeType === Node.ELEMENT_NODE) {
                let selector = el.tagName.toLowerCase();
                if (el.id) {
                    selector = '#' + el.id;
                    path.unshift(selector);
                    break;
                } else if (el.className && typeof el.className === 'string') {
                    const classes = el.className.trim().split(/\\s+/).slice(0, 2);
                    if (classes.length > 0 && classes[0]) {
                        selector += '.' + classes.join('.');
                    }
                }
                path.unshift(selector);
                el = el.parentNode;
            }
            return path.join(' > ');
        },
        
        // Get DOM tree (simplified)
        getDOMTree: function() {
            function buildTree(el, depth) {
                if (depth > 5) return null;
                const rect = el.getBoundingClientRect();
                const node = {
                    id: Math.random().toString(36).substr(2, 9),
                    tagName: el.tagName.toLowerCase(),
                    selector: window.webEditBridge.getSelector(el),
                    textContent: (el.textContent || '').trim().substring(0, 50),
                    attributes: {},
                    boundingBox: { x: rect.x, y: rect.y, width: rect.width, height: rect.height },
                    children: []
                };
                if (el.id) node.attributes.id = el.id;
                if (el.className) node.attributes.class = el.className;
                Array.from(el.children).slice(0, 20).forEach(child => {
                    const childNode = buildTree(child, depth + 1);
                    if (childNode) node.children.push(childNode);
                });
                return node;
            }
            const tree = buildTree(document.body, 0);
            this.sendToKotlin('DOMTreeUpdated', JSON.stringify({ root: tree }));
        },
        
        // Send message to Kotlin
        sendToKotlin: function(type, data) {
            if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                window.kmpJsBridge.callNative('webEditMessage', 
                    JSON.stringify({ type: type, data: data }), function() {});
            }
        }
    };
    
    // Mouse event handlers for selection mode
    document.addEventListener('mouseover', function(e) {
        if (!window.webEditBridge.selectionMode) return;
        e.stopPropagation();
        window.webEditBridge.clearHighlights();
        window.webEditBridge.highlightedElement = e.target;
        e.target.style.outline = '2px solid #2196F3';
        e.target.style.outlineOffset = '2px';
    }, true);
    
    document.addEventListener('click', function(e) {
        if (!window.webEditBridge.selectionMode) return;
        e.preventDefault();
        e.stopPropagation();
        const el = e.target;
        window.webEditBridge.selectedElement = el;
        el.style.outline = '3px solid #4CAF50';
        el.style.outlineOffset = '2px';
        const rect = el.getBoundingClientRect();
        window.webEditBridge.sendToKotlin('ElementSelected', JSON.stringify({
            element: {
                id: Math.random().toString(36).substr(2, 9),
                tagName: el.tagName.toLowerCase(),
                selector: window.webEditBridge.getSelector(el),
                textContent: (el.textContent || '').trim().substring(0, 100),
                attributes: { id: el.id || '', class: el.className || '' },
                boundingBox: { x: rect.x, y: rect.y, width: rect.width, height: rect.height }
            }
        }));
    }, true);
    
    // Notify page loaded
    window.webEditBridge.sendToKotlin('PageLoaded', JSON.stringify({
        url: window.location.href,
        title: document.title
    }));
    
    console.log('[WebEditBridge] Initialized');
})();
""".trimIndent()

