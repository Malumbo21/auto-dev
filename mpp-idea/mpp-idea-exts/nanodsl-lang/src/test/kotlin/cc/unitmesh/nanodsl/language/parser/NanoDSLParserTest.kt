package cc.unitmesh.nanodsl.language.parser

import cc.unitmesh.nanodsl.language.psi.NanoDSLFile
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class NanoDSLParserTest : LightJavaCodeInsightFixtureTestCase() {

    fun testSimpleCard() {
        val code = """
            component GreetingCard:
                Card(padding="md"):
                    VStack(spacing="sm"):
                        Text("Hello!", style="h2")
                        Text("Welcome to our app", style="body")
        """.trimIndent()

        val file = myFixture.configureByText("test.nanodsl", code) as NanoDSLFile
        assertNotNull(file)

        // Verify no parsing errors
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue("Should have no parsing errors, but found: ${errors.map { it.errorDescription }}", errors.isEmpty())
    }
    
    fun testCounterCard() {
        val code = """
            component CounterCard:
                state:
                    count: int = 1
                    price: float = 99.0

                Card:
                    padding: "lg"
                    content:
                        VStack:
                            Text(content << f"Total: ${'$'}{state.count * state.price}")

                            HStack:
                                Button("-"):
                                    on_click: state.count -= 1

                                Input(value := state.count)

                                Button("+"):
                                    on_click: state.count += 1
        """.trimIndent()

        val file = myFixture.configureByText("test.nanodsl", code) as NanoDSLFile
        assertNotNull(file)

        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue("Should have no parsing errors, but found: ${errors.map { it.errorDescription }}", errors.isEmpty())
    }

    fun testLoginForm() {
        val code = """
            component LoginForm:
                state:
                    email: string = ""
                    password: string = ""
                    is_loading: bool = false

                Card:
                    padding: "lg"
                    shadow: "md"
                    content:
                        VStack(spacing="md"):
                            Text("Sign In", style="h2")

                            Input(value := state.email, placeholder="Email")
                            Input(value := state.password, placeholder="Password", type="password")

                            Button("Login", intent="primary"):
                                on_click:
                                    state.is_loading = true
                                    Fetch(url="/api/login", method="POST")

                            HStack(justify="center"):
                                Text("Don't have an account?", style="caption")
                                Button("Sign Up", intent="secondary"):
                                    on_click: Navigate(to="/signup")
        """.trimIndent()

        val file = myFixture.configureByText("test.nanodsl", code) as NanoDSLFile
        assertNotNull(file)

        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue("Should have no parsing errors, but found: ${errors.map { it.errorDescription }}", errors.isEmpty())
    }

    fun testProductCard() {
        val code = """
            component ProductCard(item: Product):
                padding: "md"
                shadow: "sm"

                content:
                    VStack(spacing="sm"):
                        Image(src=item.image, aspect=16/9, radius="md")

                        HStack(align="center", justify="between"):
                            Text(item.title, style="h3")
                            if item.is_new:
                                Badge("New", color="green")

                        Text(item.description, style="body", limit=2)

                        HStack(spacing="sm"):
                            Text(f"${'$'}{item.price}", style="h3")
                            Button("Add to Cart", intent="primary", icon="cart")
        """.trimIndent()

        val file = myFixture.configureByText("test.nanodsl", code) as NanoDSLFile
        assertNotNull(file)

        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue("Should have no parsing errors, but found: ${errors.map { it.errorDescription }}", errors.isEmpty())
    }

    fun testTaskList() {
        val code = """
            component TaskList:
                state:
                    tasks: list = []
                    new_task: string = ""

                VStack(spacing="md"):
                    HStack(spacing="sm"):
                        Input(value := state.new_task, placeholder="Add a task")
                        Button("Add"):
                            on_click: state.tasks += state.new_task

                    for task in state.tasks:
                        HStack(spacing="sm"):
                            Checkbox(checked := task.done)
                            Text(task.title)
        """.trimIndent()

        val file = myFixture.configureByText("test.nanodsl", code) as NanoDSLFile
        assertNotNull(file)

        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue("Should have no parsing errors, but found: ${errors.map { it.errorDescription }}", errors.isEmpty())
    }
}

