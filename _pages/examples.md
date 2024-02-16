---
title: "Code Examples"
permalink: /examples/
author_profile: false
---
{% assign example_webcomponents = "" | split: "," %}
{% for example_field in site.examples %}
    {% assign example = example_field[1] %}
    {% if example.type == "WebComponent" %}
        {% assign example_webcomponents = example_webcomponents | push: example %}
    {% else %}
        {% for post in site.posts %}
            {% if post.examples contains example_field[0] %}
                {% include github_project.html name=example.name url=example.url description=example.description type=example.type %}
            {% endif %}
        {% endfor %}
    {% endif %}
{% endfor %}

{% if example_webcomponents %}
## Web Components
{% for example in example_webcomponents %}
    {% include github_project.html name=example.name url=example.url description=example.description type=example.type %}
{% endfor %}
{% endif %}