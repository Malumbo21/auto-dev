// E2E Test DSL Examples - Generated Test Cases
// These examples demonstrate the DSL syntax for various testing scenarios

// ============================================
// Test Case 1: User Login Flow
// ============================================
scenario "User Login Flow" {
    description "Test standard user login with valid credentials"
    url "https://example.com/login"
    tags ["login", "auth", "smoke"]
    priority high

    step "Enter username" {
        type #1 "testuser@example.com"
        expect "Username field should be filled"
    }

    step "Enter password" {
        type #2 "SecurePassword123" clearFirst
        expect "Password field should be filled"
    }

    step "Click login button" {
        click #3
        expect "Login form should be submitted"
        timeout 10000
    }

    step "Wait for dashboard" {
        wait urlContains "/dashboard"
        timeout 15000
    }

    step "Verify welcome message" {
        assert #4 textContains "Welcome"
        expect "User should see welcome message"
    }
}

// ============================================
// Test Case 2: Product Search and Filter
// ============================================
scenario "Product Search and Filter" {
    description "Test product search functionality with filters"
    url "https://example.com/products"
    tags ["search", "products", "e2e"]
    priority medium

    step "Enter search term" {
        type #1 "laptop" pressEnter
        expect "Search should be triggered"
    }

    step "Wait for results" {
        wait visible #2
        timeout 5000
    }

    step "Apply price filter" {
        click #3
    }

    step "Select price range" {
        select #4 value "500-1000"
    }

    step "Apply filter" {
        click #5
    }

    step "Verify filtered results" {
        assert #6 visible
        expect "Filtered products should be displayed"
    }
}

// ============================================
// Test Case 3: Shopping Cart Operations
// ============================================
scenario "Shopping Cart Operations" {
    description "Test adding and removing items from cart"
    url "https://example.com/products/laptop-1"
    tags ["cart", "shopping", "critical"]
    priority critical

    step "Add to cart" {
        click #1
        expect "Product should be added to cart"
    }

    step "Wait for cart update" {
        wait textPresent "Added to cart"
        timeout 3000
    }

    step "Go to cart" {
        click #2
    }

    step "Verify item in cart" {
        assert #3 visible
        expect "Product should appear in cart"
    }

    step "Increase quantity" {
        click #4
    }

    step "Verify quantity updated" {
        assert #5 textEquals "2"
    }

    step "Remove item" {
        click #6
    }

    step "Verify cart empty" {
        assert #7 textContains "Your cart is empty"
    }
}

// ============================================
// Test Case 4: User Registration
// ============================================
scenario "User Registration" {
    description "Test new user registration flow"
    url "https://example.com/register"
    tags ["registration", "auth", "smoke"]
    priority high

    step "Enter email" {
        type #1 "newuser@example.com"
    }

    step "Enter password" {
        type #2 "StrongPass123!"
    }

    step "Confirm password" {
        type #3 "StrongPass123!"
    }

    step "Enter name" {
        type #4 "John Doe"
    }

    step "Accept terms" {
        click #5
    }

    step "Submit registration" {
        click #6
        timeout 10000
    }

    step "Verify success message" {
        wait textPresent "Registration successful"
        timeout 5000
    }

    step "Verify redirect to login" {
        wait urlContains "/login"
    }
}

// ============================================
// Test Case 5: Form Validation
// ============================================
scenario "Form Validation" {
    description "Test form validation error messages"
    url "https://example.com/contact"
    tags ["form", "validation", "ui"]
    priority medium

    step "Submit empty form" {
        click #1
    }

    step "Verify email error" {
        assert #2 visible
        assert #2 textContains "Email is required"
    }

    step "Enter invalid email" {
        type #3 "invalid-email"
    }

    step "Submit form" {
        click #1
    }

    step "Verify email format error" {
        assert #2 textContains "Invalid email format"
    }

    step "Enter valid email" {
        type #3 "valid@example.com" clearFirst
    }

    step "Submit form" {
        click #1
    }

    step "Verify success" {
        wait textPresent "Message sent"
    }
}

// ============================================
// Test Case 6: Navigation and Breadcrumbs
// ============================================
scenario "Navigation and Breadcrumbs" {
    description "Test site navigation and breadcrumb functionality"
    url "https://example.com"
    tags ["navigation", "ui"]
    priority low

    step "Click products menu" {
        click #1
    }

    step "Wait for products page" {
        wait urlContains "/products"
    }

    step "Click category" {
        click #2
    }

    step "Verify breadcrumb" {
        assert #3 visible
        assert #3 textContains "Products"
    }

    step "Click breadcrumb home" {
        click #4
    }

    step "Verify home page" {
        wait urlContains "/"
    }

    step "Go back" {
        goBack
    }

    step "Verify category page" {
        wait urlContains "/category"
    }
}

// ============================================
// Test Case 7: Modal Dialog Interactions
// ============================================
scenario "Modal Dialog Interactions" {
    description "Test modal dialog open, close, and form submission"
    url "https://example.com/dashboard"
    tags ["modal", "ui", "dialog"]
    priority medium

    step "Click open modal button" {
        click #1
    }

    step "Wait for modal" {
        wait visible #2
        timeout 2000
    }

    step "Verify modal title" {
        assert #3 textEquals "Confirm Action"
    }

    step "Click cancel" {
        click #4
    }

    step "Verify modal closed" {
        wait hidden #2
    }

    step "Reopen modal" {
        click #1
    }

    step "Click confirm" {
        click #5
    }

    step "Verify action completed" {
        wait textPresent "Action completed"
    }
}

// ============================================
// Test Case 8: Infinite Scroll
// ============================================
scenario "Infinite Scroll" {
    description "Test infinite scroll loading of content"
    url "https://example.com/feed"
    tags ["scroll", "loading", "performance"]
    priority low

    step "Wait for initial content" {
        wait visible #1
    }

    step "Count initial items" {
        assert #2 visible
    }

    step "Scroll down" {
        scroll down 500
    }

    step "Wait for loading" {
        wait visible #3
        timeout 3000
    }

    step "Wait for new content" {
        wait hidden #3
        timeout 5000
    }

    step "Scroll down again" {
        scroll down 500
    }

    step "Verify more content loaded" {
        assert #4 visible
    }
}

// ============================================
// Test Case 9: File Upload
// ============================================
scenario "File Upload" {
    description "Test file upload functionality"
    url "https://example.com/upload"
    tags ["upload", "file", "form"]
    priority medium

    step "Click upload area" {
        click #1
    }

    step "Upload file" {
        uploadFile #2 "/path/to/test-file.pdf"
    }

    step "Wait for upload" {
        wait textPresent "Upload complete"
        timeout 30000
    }

    step "Verify file name displayed" {
        assert #3 textContains "test-file.pdf"
    }

    step "Click submit" {
        click #4
    }

    step "Verify success" {
        wait textPresent "File submitted successfully"
    }
}

// ============================================
// Test Case 10: Keyboard Shortcuts
// ============================================
scenario "Keyboard Shortcuts" {
    description "Test keyboard shortcuts functionality"
    url "https://example.com/editor"
    tags ["keyboard", "shortcuts", "accessibility"]
    priority low

    step "Focus editor" {
        click #1
    }

    step "Type content" {
        type #1 "Hello World"
    }

    step "Select all with Ctrl+A" {
        pressKey "a" ctrl
    }

    step "Copy with Ctrl+C" {
        pressKey "c" ctrl
    }

    step "Move to end" {
        pressKey "End"
    }

    step "Paste with Ctrl+V" {
        pressKey "v" ctrl
    }

    step "Verify content duplicated" {
        assert #1 textContains "Hello WorldHello World"
    }

    step "Undo with Ctrl+Z" {
        pressKey "z" ctrl
    }

    step "Verify undo worked" {
        assert #1 textEquals "Hello World"
    }

    step "Save with Ctrl+S" {
        pressKey "s" ctrl
    }

    step "Verify saved" {
        wait textPresent "Saved"
    }
}
