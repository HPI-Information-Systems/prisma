FROM python:3.11
EXPOSE 5004

RUN mkdir /PRISMA
WORKDIR /PRISMA

# Install dependencies
RUN apt update && apt install -y tini && rm -rf /var/lib/apt/lists/*
RUN pip install poetry
COPY poetry.lock .
COPY pyproject.toml .
RUN poetry install

# Only copy remaining files now so that we can avoid having to reinstall dependencies every time some other file changes.
COPY . .

ENTRYPOINT ["tini", "--", "poetry", "run", "python", "prisma_server.py"]
