component TravelPlan:
    state:
        flights: list = [
            {
                "flightNumber": "CA1234",
                "airline": "Air China",
                "departure": "PEK",
                "arrival": "SHA",
                "time": "08:30",
                "price": 1280
            },
            {
                "flightNumber": "MU5678",
                "airline": "China Eastern",
                "departure": "PEK",
                "arrival": "SHA",
                "time": "10:15",
                "price": 1150
            }
        ]

    for flight in state.flights:
        Card:
            VStack:
                Text(flight["airline"], style="h3")
                Text("Flight: {flight['flightNumber']}", style="body")
                Text("{flight['departure']} -> {flight['arrival']}  {flight['time']}", style="caption")
                Text("Price: {flight['price']}", style="body")
