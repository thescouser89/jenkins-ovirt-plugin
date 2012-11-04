
NAME=ovirt
DIR=$(NAME)
PLUGIN_FILE=$(NAME).hpi
PLUGIN=$(DIR)/target/$(PLUGIN_FILE)


all: build

build: clean
	cd $(DIR); mvn install
	cp $(PLUGIN) .

clean:
	cd $(DIR); mvn clean
	rm -f $(PLUGIN_FILE)

