component TravelPlan:
    state:
        transport: str = "train"
        days: int = 3
        budget: dict = {"transport": 800, "hotel": 1200, "food": 600, "tickets": 400}
        checklist: dict = {"id": true, "clothes": false, "medicine": false, "camera": false}
        notes: str = ""

    VStack(spacing="lg"):
        Card(padding="md", shadow="sm"):
            Text("预算估算", style="h3")
            VStack(spacing="sm"):
                HStack(justify="between"):
                    HStack:
                        Icon("train", size="sm")
                        Text("交通", style="body")
                    Text("¥{state.budget.transport}", style="body")

                HStack(justify="between"):
                    HStack:
                        Icon("hotel", size="sm")
                        Text("住宿", style="body")
                    Text("¥{state.budget.hotel}", style="body")

                HStack(justify="between"):
                    HStack:
                        Icon("restaurant", size="sm")
                        Text("餐饮", style="body")
                    Text("¥{state.budget.food}", style="body")

                HStack(justify="between"):
                    HStack:
                        Icon("confirmation-number", size="sm")
                        Text("门票", style="body")
                    Text("¥{state.budget.tickets}", style="body")

                Divider

                HStack(justify="between"):
                    Text("总计", style="h3")
                    Text("¥{state.budget.transport + state.budget.hotel + state.budget.food + state.budget.tickets}", style="h3", color="danger")
