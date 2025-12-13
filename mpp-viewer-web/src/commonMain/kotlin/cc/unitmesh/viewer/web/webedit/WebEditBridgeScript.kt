package cc.unitmesh.viewer.web.webedit

/**
 * JavaScript code to inject into WebView for DOM selection and communication
 *
 * This script provides:
 * - Inspect mode with Shadow DOM support
 * - Visual overlay highlighting (isolated via Shadow DOM)
 * - DOM tree extraction with shadow roots
 * - MutationObserver for real-time updates
 * - Bidirectional communication with Kotlin via kmpJsBridge
 */
fun getWebEditBridgeScript(): String = """
(function() {
    // Prevent multiple injections
    if (window.webEditBridge) return;

    // ========== Shadow DOM Inspect Overlay ==========
    // Create isolated overlay container using Shadow DOM
    const overlayHost = document.createElement('div');
    overlayHost.id = 'webedit-inspect-overlay-host';
    overlayHost.style.cssText = 'position: fixed; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; z-index: 2147483647;';

    const shadowRoot = overlayHost.attachShadow({ mode: 'open' });
    shadowRoot.innerHTML = `
        <style>
            :host {
                all: initial;
            }
            .highlight-box {
                position: absolute;
                pointer-events: none;
                box-sizing: border-box;
                transition: all 0.1s ease;
            }
            .highlight-hover {
                border: 2px solid #2196F3;
                background: rgba(33, 150, 243, 0.1);
            }
            .highlight-selected {
                border: 3px solid #4CAF50;
                background: rgba(76, 175, 80, 0.15);
            }
            .highlight-label {
                position: absolute;
                top: -22px;
                left: 0;
                background: #2196F3;
                color: white;
                padding: 2px 6px;
                font-size: 11px;
                font-family: monospace;
                border-radius: 2px;
                white-space: nowrap;
                max-width: 300px;
                overflow: hidden;
                text-overflow: ellipsis;
            }
        </style>
        <div id="hover-box" class="highlight-box highlight-hover" style="display: none;">
            <div class="highlight-label"></div>
        </div>
        <div id="selected-box" class="highlight-box highlight-selected" style="display: none;">
            <div class="highlight-label"></div>
        </div>
    `;

    document.body.appendChild(overlayHost);

    const hoverBox = shadowRoot.getElementById('hover-box');
    const selectedBox = shadowRoot.getElementById('selected-box');

    // ========== Shadow DOM Utilities ==========
    // Query all elements including shadow DOM
    function querySelectorAllDeep(selector, root = document) {
        const elements = [];

        function traverse(node) {
            if (node.nodeType === Node.ELEMENT_NODE) {
                if (node.matches && node.matches(selector)) {
                    elements.push(node);
                }

                // Traverse shadow root if exists
                if (node.shadowRoot) {
                    traverse(node.shadowRoot);
                }
            }

            // Traverse children (only elements)
            const children = node.children || [];
            for (let child of children) {
                if (child.nodeType === Node.ELEMENT_NODE) {
                    traverse(child);
                }
            }
        }

        traverse(root);
        return elements;
    }

    // Get element at point (pierce shadow DOM)
    function getElementAtPoint(x, y) {
        let element = document.elementFromPoint(x, y);

        // Pierce through shadow roots
        while (element && element.shadowRoot) {
            const shadowElement = element.shadowRoot.elementFromPoint(x, y);
            if (!shadowElement || shadowElement === element) break;
            element = shadowElement;
        }

        // Ignore our own overlay
        if (element && element.closest('#webedit-inspect-overlay-host')) {
            return null;
        }

        return element;
    }

    // WebEdit Bridge object
    window.webEditBridge = {
        inspectMode: false,
        selectionMode: false,
        highlightedElement: null,
        selectedElement: null,
        mutationObserver: null,

        // ========== Inspect Mode Management ==========
        enableInspectMode: function() {
            this.inspectMode = true;
            this.selectionMode = true;
            overlayHost.style.pointerEvents = 'none';
            this.startMutationObserver();
            console.log('[WebEditBridge] Inspect mode enabled');
        },

        disableInspectMode: function() {
            this.inspectMode = false;
            this.selectionMode = false;
            this.clearHoverHighlight();
            this.clearSelection();
            this.stopMutationObserver();
            console.log('[WebEditBridge] Inspect mode disabled');
        },

        // Legacy: Enable/disable selection mode
        setSelectionMode: function(enabled) {
            if (enabled) {
                this.enableInspectMode();
            } else {
                this.disableInspectMode();
            }
        },

        // ========== Visual Highlighting ==========
        updateHighlightBox: function(element, box, label) {
            if (!element) {
                box.style.display = 'none';
                return;
            }

            const rect = element.getBoundingClientRect();
            box.style.display = 'block';
            box.style.left = rect.left + 'px';
            box.style.top = rect.top + 'px';
            box.style.width = rect.width + 'px';
            box.style.height = rect.height + 'px';

            const labelEl = box.querySelector('.highlight-label');
            if (labelEl) {
                labelEl.textContent = this.getElementLabel(element);
            }
        },

        getElementLabel: function(el) {
            const tag = el.tagName.toLowerCase();
            const id = el.id ? '#' + el.id : '';
            const classes = el.className && typeof el.className === 'string'
                ? '.' + el.className.trim().split(/\\s+/).slice(0, 2).join('.')
                : '';
            return tag + id + classes;
        },

        // Highlight a specific element by selector (supports shadow DOM)
        highlightElement: function(selector) {
            this.clearHoverHighlight();
            try {
                const elements = querySelectorAllDeep(selector);
                const el = elements[0];
                if (el) {
                    this.highlightedElement = el;
                    this.updateHighlightBox(el, hoverBox);
                }
            } catch(e) {
                console.error('[WebEditBridge] highlightElement error:', e);
            }
        },

        // Clear hover highlight only (keep selection)
        clearHoverHighlight: function() {
            this.highlightedElement = null;
            hoverBox.style.display = 'none';
        },

        // Clear selection highlight
        clearSelection: function() {
            this.selectedElement = null;
            selectedBox.style.display = 'none';
        },

        // Clear all highlights (for backward compatibility)
        clearHighlights: function() {
            this.clearHoverHighlight();
            this.clearSelection();
        },

        // ========== MutationObserver for DOM Changes ==========
        startMutationObserver: function() {
            if (this.mutationObserver) return;

            this.mutationObserver = new MutationObserver((mutations) => {
                // Update highlights if elements are still in DOM
                if (this.highlightedElement && !document.contains(this.highlightedElement)) {
                    this.clearHoverHighlight();
                }
                if (this.selectedElement && !document.contains(this.selectedElement)) {
                    this.clearSelection();
                } else if (this.selectedElement) {
                    // Update position if element moved
                    this.updateHighlightBox(this.selectedElement, selectedBox);
                }

                // Notify Kotlin about DOM changes
                this.sendToKotlin('DOMChanged', {
                    mutationCount: mutations.length
                });
            });

            this.mutationObserver.observe(document.body, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ['class', 'id', 'style']
            });
        },

        stopMutationObserver: function() {
            if (this.mutationObserver) {
                this.mutationObserver.disconnect();
                this.mutationObserver = null;
            }
        },

        // Scroll to element (supports shadow DOM)
        scrollToElement: function(selector) {
            try {
                const elements = querySelectorAllDeep(selector);
                const el = elements[0];
                if (el) {
                    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
            } catch(e) {
                console.error('[WebEditBridge] scrollToElement error:', e);
            }
        },

        // Get element at specific coordinates
        getElementAtPoint: function(x, y) {
            const el = getElementAtPoint(x, y);
            if (!el) return null;

            const rect = el.getBoundingClientRect();
            return {
                id: (typeof crypto !== 'undefined' && crypto.randomUUID) ? crypto.randomUUID() : (Date.now().toString(36) + Math.random().toString(36).slice(2, 11)),
                tagName: el.tagName.toLowerCase(),
                selector: this.getSelector(el),
                textContent: (el.textContent || '').trim().substring(0, 100),
                attributes: this.getElementAttributes(el),
                boundingBox: { x: rect.x, y: rect.y, width: rect.width, height: rect.height },
                isShadowHost: !!el.shadowRoot
            };
        },

        // Get element attributes
        getElementAttributes: function(el) {
            const attrs = {};
            if (el.id) attrs.id = el.id;
            if (el.className && typeof el.className === 'string') attrs.class = el.className;

            // Add common attributes
            ['href', 'src', 'alt', 'title', 'type', 'name', 'value'].forEach(attr => {
                if (el.hasAttribute(attr)) {
                    attrs[attr] = el.getAttribute(attr);
                }
            });

            return attrs;
        },

        // Get unique selector for element (shadow DOM aware)
        getSelector: function(el) {
            if (el.id) return '#' + el.id;
            if (el === document.body) return 'body';
            if (el === document.documentElement) return 'html';

            const path = [];
            let current = el;

            while (current && current.nodeType === Node.ELEMENT_NODE) {
                let selector = current.tagName.toLowerCase();

                if (current.id) {
                    selector = '#' + current.id;
                    path.unshift(selector);
                    break;
                } else if (current.className && typeof current.className === 'string') {
                    const classes = current.className.trim().split(/\\s+/).slice(0, 2);
                    if (classes.length > 0 && classes[0]) {
                        selector += '.' + classes.join('.');
                    }
                }

                path.unshift(selector);

                // Handle shadow root boundary
                if (current.parentNode && current.parentNode.nodeType === Node.DOCUMENT_FRAGMENT_NODE) {
                    path.unshift('::shadow');
                    current = current.parentNode.host;
                } else {
                    current = current.parentNode;
                }
            }

            return path.join(' > ');
        },

        // Get DOM tree with Shadow DOM support
        getDOMTree: function() {
            const self = this;

            function buildTree(el, depth, isInShadow) {
                if (depth > 8) return null;

                const rect = el.getBoundingClientRect();
                const node = {
                    id: (typeof crypto !== 'undefined' && crypto.randomUUID) ? crypto.randomUUID() : (Date.now().toString(36) + Math.random().toString(36).slice(2, 11)),
                    tagName: el.tagName.toLowerCase(),
                    selector: self.getSelector(el),
                    textContent: (el.textContent || '').trim().substring(0, 50),
                    attributes: self.getElementAttributes(el),
                    boundingBox: { x: rect.x, y: rect.y, width: rect.width, height: rect.height },
                    children: [],
                    isShadowHost: !!el.shadowRoot,
                    inShadowRoot: isInShadow || false
                };

                // Process regular children
                Array.from(el.children).slice(0, 30).forEach(child => {
                    const childNode = buildTree(child, depth + 1, isInShadow);
                    if (childNode) node.children.push(childNode);
                });

                // Process shadow DOM children
                if (el.shadowRoot) {
                    Array.from(el.shadowRoot.children).slice(0, 30).forEach(child => {
                        const childNode = buildTree(child, depth + 1, true);
                        if (childNode) {
                            node.children.push(childNode);
                        }
                    });
                }

                return node;
            }

            const tree = buildTree(document.body, 0, false);
            this.sendToKotlin('DOMTreeUpdated', { root: tree });
        },

        // Send message to Kotlin
        sendToKotlin: function(type, data) {
            if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                window.kmpJsBridge.callNative('webEditMessage',
                    JSON.stringify({ type: type, data: data }), function() {});
            }
        }
    };

    // ========== Event Handlers for Inspect Mode ==========
    let lastHoverTime = 0;
    const HOVER_THROTTLE = 50; // ms

    document.addEventListener('mousemove', function(e) {
        if (!window.webEditBridge.inspectMode) return;

        const now = Date.now();
        if (now - lastHoverTime < HOVER_THROTTLE) return;
        lastHoverTime = now;

        const el = getElementAtPoint(e.clientX, e.clientY);
        if (!el || el === window.webEditBridge.highlightedElement) return;

        window.webEditBridge.highlightedElement = el;
        window.webEditBridge.updateHighlightBox(el, hoverBox);
    }, true);

    document.addEventListener('click', function(e) {
        if (!window.webEditBridge.inspectMode) return;
        e.preventDefault();
        e.stopPropagation();

        const el = getElementAtPoint(e.clientX, e.clientY);
        if (!el) return;

        window.webEditBridge.selectedElement = el;
        window.webEditBridge.updateHighlightBox(el, selectedBox);

        const rect = el.getBoundingClientRect();
        window.webEditBridge.sendToKotlin('ElementSelected', {
            element: {
                id: (typeof crypto !== 'undefined' && crypto.randomUUID) ? crypto.randomUUID() : (Date.now().toString(36) + Math.random().toString(36).slice(2, 11)),
                tagName: el.tagName.toLowerCase(),
                selector: window.webEditBridge.getSelector(el),
                textContent: (el.textContent || '').trim().substring(0, 100),
                attributes: window.webEditBridge.getElementAttributes(el),
                boundingBox: { x: rect.x, y: rect.y, width: rect.width, height: rect.height },
                isShadowHost: !!el.shadowRoot
            }
        });
    }, true);

    // Update highlight positions on scroll/resize
    window.addEventListener('scroll', function() {
        if (window.webEditBridge.highlightedElement) {
            window.webEditBridge.updateHighlightBox(window.webEditBridge.highlightedElement, hoverBox);
        }
        if (window.webEditBridge.selectedElement) {
            window.webEditBridge.updateHighlightBox(window.webEditBridge.selectedElement, selectedBox);
        }
    }, true);

    window.addEventListener('resize', function() {
        if (window.webEditBridge.highlightedElement) {
            window.webEditBridge.updateHighlightBox(window.webEditBridge.highlightedElement, hoverBox);
        }
        if (window.webEditBridge.selectedElement) {
            window.webEditBridge.updateHighlightBox(window.webEditBridge.selectedElement, selectedBox);
        }
    });

    // Notify page loaded
    window.webEditBridge.sendToKotlin('PageLoaded', {
        url: window.location.href,
        title: document.title
    });

    console.log('[WebEditBridge] Initialized with Shadow DOM support');
})();
""".trimIndent()

