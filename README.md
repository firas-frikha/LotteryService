# Lottery System

This is a gRPC-based Lottery System built using the Akka framework, leveraging event sourcing, CQRS, and cluster
sharding to manage stateful entities efficiently. The system allows the creation and participation in lotteries, with
automatic closing and winner selection.

## **Usage**

This project exposes several gRPC routes for managing lotteries. The main entity is the Lottery with three states:
**Empty**, **Active**, and **Closed**.

### **Lottery States**

* **Empty** : Initial state of the lottery.
* **Active** : State where participants can join.
* **Closed**: State where no more participants can join, and a winner is selected.

### **Commands**

* **CreateCommand**: Transitions a lottery from Empty to Active.
* **ParticipateCommand**: Adds a participant to an Active lottery.
* **CloseLotteryCommand**: Transitions a lottery from Active to Closed and selects a random winner. Automatically
  executed at midnight for all active lotteries.

### **gRPC Routes**

* **AdminCreateLottery**: Responsible for creating a lottery by issuing the CreateCommand.
* **ParticipateInLottery**: Allows participants to join an active lottery using the ParticipateCommand.
* **FetchOpenLotteries**: Fetches all lotteries currently in the Active state.
* **FetchLotteriesResultsByDate**: Fetches lottery results (winners) for lotteries closed on a specific date.

## **Running the Service Locally**

### Step 1: Set the Configuration Path:

The application requires a configuration file (application.conf) to be properly set. Start by exposing the CONFIG_PATH
environment variable, which should point to the application.conf file.

Run the following command to set the path:

`export CONFIG_PATH=src/main/resources/application.conf
`

### Step 2: Set Up PostgreSQL Database:

The service requires a PostgreSQL instance to store event journal tables, projection tables, and read tables. To set up
PostgreSQL, you can use Docker to spin up a PostgreSQL container using the provided docker-compose file in the project.

Run the following command to start the database:

`docker-compose up
`

### Step 3: Create Database Tables:

Once the PostgreSQL instance is running, you will need to create the required tables: event journal tables, projection
tables, and the read tables for lotteries and participants.

You can create these tables by executing the following commands:

1. #### Create event journal tables:

`docker exec -i lottery-db psql -U lotteryservice -t < tables/event_tables.sql
`

2. ### Create the lottery service tables:

`docker exec -i lottery-db psql -U lotteryservice -t < tables/lottery_service_tables.sql
`

3. ### Create projection tables:

`docker exec -i lottery-db psql -U lotteryservice -t < tables/projection_tables.sql
`

### Step 4: Run the Service:

After setting up the database and configuration, you can now run the service. The main entry point for the application
is located in the LotteryManagementApplication class inside the grpcserver module.

### Step5: Access the gRPC Server:

Once the service is running, it will be available at the address [localhost:8080]().

You can use grpcui to explore the available routes and interact with the service. To do so, run:

`grpcui -plaintext localhost:8080
`
This will open a web UI where you can view the available gRPC routes and test the service.

### **Build**

To build the project, run the following command:

`sbt run
`

### **Testing**

The project includes unit tests for all project modules.

### Run unit tests:

`sbt test
`

### Test Coverage:

`sbt coverage test coverageReport
`
