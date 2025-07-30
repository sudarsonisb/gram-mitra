import json
import os
from typing import List, Dict, Any, Set
from collections import defaultdict

class JSONGraphConnector:
    def __init__(self, json_file_path="plant_disease_graph.json"):
        self.json_file_path = json_file_path
        self.graph_data = self.load_graph_data()
        self.nodes_by_id = {node['id']: node for node in self.graph_data.get('nodes', [])}
        self.relationships = self.graph_data.get('relationships', [])
        self.build_indices()
    
    def load_graph_data(self):
        """Load graph data from JSON file"""
        if os.path.exists(self.json_file_path):
            with open(self.json_file_path, 'r', encoding='utf-8') as f:
                return json.load(f)
        else:
            # Return empty graph structure if file doesn't exist
            print(f"Warning: {self.json_file_path} not found. Using empty graph.")
            return {"nodes": [], "relationships": []}
    
    def build_indices(self):
        """Build indices for faster querying"""
        # Index nodes by type
        self.nodes_by_type = defaultdict(list)
        for node in self.graph_data.get('nodes', []):
            node_type = node.get('type', 'Unknown')
            self.nodes_by_type[node_type].append(node)
        
        # Index relationships by source and target
        self.relationships_by_source = defaultdict(list)
        self.relationships_by_target = defaultdict(list)
        self.relationships_by_type = defaultdict(list)
        
        for rel in self.relationships:
            source = rel.get('source')
            target = rel.get('target')
            rel_type = rel.get('type')
            
            if source:
                self.relationships_by_source[source].append(rel)
            if target:
                self.relationships_by_target[target].append(rel)
            if rel_type:
                self.relationships_by_type[rel_type].append(rel)
    
    def close(self):
        """Compatibility method - no cleanup needed for JSON"""
        pass
    
    def execute_query(self, query, parameters=None):
        """
        Execute a 'query' - this maps Cypher-like operations to JSON operations
        Returns list of dictionaries to match Neo4j driver format
        """
        try:
            # This is a simplified approach - in practice you'd parse the query
            # For now, we'll handle specific query patterns
            return []
        except Exception as e:
            print(f"Query error: {e}")
            return []
    
    def get_schema(self):
        """Get the graph schema information"""
        try:
            # Get unique node types
            node_types = list(set(node.get('type', 'Unknown') for node in self.graph_data.get('nodes', [])))
            
            # Get unique relationship types  
            rel_types = list(set(rel.get('type', 'Unknown') for rel in self.relationships))
            
            # Get node counts by type
            type_counts = []
            for node_type in node_types:
                count = len(self.nodes_by_type[node_type])
                type_counts.append(([node_type], count))
            
            # Sort by count descending
            type_counts.sort(key=lambda x: x[1], reverse=True)
            
            return {
                'node_labels': node_types,
                'relationship_types': rel_types,
                'node_counts': type_counts[:10]
            }
        except Exception as e:
            print(f"Error getting schema: {e}")
            return {'node_labels': [], 'relationship_types': [], 'node_counts': []}
    
    def find_diseases_by_symptoms(self, symptoms):
        """
        JSON equivalent of:
        MATCH (d)-[r]-(s)
        WHERE (d:Disease OR d:PlantDisease OR 'Disease' IN labels(d))
        WITH d, collect(DISTINCT s.name) as all_symptoms
        WITH d, all_symptoms,
             [sym IN {symptoms_list} WHERE ANY(ds IN all_symptoms WHERE toLower(ds) CONTAINS toLower(sym))] as matched
        WHERE size(matched) > 0
        RETURN d.name as disease, d.description as description, all_symptoms, matched,
               size(matched) as match_count, size(all_symptoms) as total_symptoms,
               toFloat(size(matched)) / size(all_symptoms) as match_percentage
        ORDER BY match_percentage DESC, match_count DESC LIMIT 10
        """
        if not symptoms:
            return []
        
        symptoms_lower = [self._clean_symptom_text(s.lower()) for s in symptoms]
        disease_results = []
        
        # Get all disease nodes
        disease_nodes = []
        for node_type in ['Disease', 'PlantDisease']:
            disease_nodes.extend(self.nodes_by_type.get(node_type, []))
        
        for disease_node in disease_nodes:
            disease_id = disease_node['id']
            disease_name = disease_node.get('name', '')
            disease_description = disease_node.get('description', '')
            
            # Find all symptoms connected to this disease via MANIFESTS_AS or HAS_SYMPTOM
            all_disease_symptoms = []
            for rel in self.relationships_by_source.get(disease_id, []):
                if rel.get('type') in ['HAS_SYMPTOM', 'MANIFESTS_AS', 'SHOWS', 'EXHIBITS']:
                    target_node = self.nodes_by_id.get(rel['target'])
                    if target_node and target_node.get('type') == 'Symptom':
                        symptom_name = target_node.get('name', '')
                        if symptom_name:
                            # Clean the symptom text
                            clean_symptom = self._clean_symptom_text(symptom_name)
                            if clean_symptom:
                                all_disease_symptoms.append(clean_symptom)
            
            # Remove duplicates
            all_disease_symptoms = list(set(all_disease_symptoms))
            
            # Find matching symptoms using fuzzy matching
            matched_symptoms = []
            for user_symptom in symptoms_lower:
                for disease_symptom in all_disease_symptoms:
                    if self._symptoms_match(user_symptom, disease_symptom):
                        matched_symptoms.append(disease_symptom)
                        break
            
            if matched_symptoms:
                match_count = len(matched_symptoms)
                total_symptoms = max(len(all_disease_symptoms), 1)
                match_percentage = match_count / total_symptoms
                
                disease_results.append({
                    'disease': disease_name,
                    'description': disease_description,
                    'all_symptoms': all_disease_symptoms,
                    'matched': matched_symptoms,
                    'match_count': match_count,
                    'total_symptoms': total_symptoms,
                    'match_percentage': match_percentage
                })
        
        # Sort by match percentage and count
        disease_results.sort(key=lambda x: (x['match_percentage'], x['match_count']), reverse=True)
        return disease_results[:10]
    
    def _clean_symptom_text(self, text):
        """Clean symptom text by removing extra spaces and punctuation"""
        if not text:
            return ""
        
        # Remove leading/trailing spaces and punctuation
        cleaned = text.strip().strip('.,;:!?')
        
        # Replace multiple spaces with single space
        import re
        cleaned = re.sub(r'\s+', ' ', cleaned)
        
        return cleaned.lower()
    
    def _symptoms_match(self, user_symptom, disease_symptom):
        """Check if user symptom matches disease symptom using fuzzy logic"""
        user_symptom = user_symptom.lower().strip()
        disease_symptom = disease_symptom.lower().strip()
        
        # Exact match
        if user_symptom == disease_symptom:
            return True
        
        # Substring match (either direction)
        if user_symptom in disease_symptom or disease_symptom in user_symptom:
            return True
        
        # Word-based matching
        user_words = set(user_symptom.split())
        disease_words = set(disease_symptom.split())
        
        # Remove common stop words
        stop_words = {'on', 'the', 'of', 'in', 'at', 'with', 'and', 'or', 'a', 'an'}
        user_words = user_words - stop_words
        disease_words = disease_words - stop_words
        
        if not user_words or not disease_words:
            return False
        
        # Check for significant word overlap (>50%)
        intersection = user_words.intersection(disease_words)
        union = user_words.union(disease_words)
        
        if len(intersection) / len(union) > 0.5:
            return True
        
        # Check for key symptom keywords
        symptom_keywords = {
            'yellow': ['yellowing', 'chlorosis'],
            'brown': ['browning', 'necrosis'],
            'wilt': ['wilting', 'drooping'],
            'spot': ['spots', 'lesions', 'patches'],
            'dry': ['drying', 'dried', 'dessication'],
            'rot': ['rotting', 'decay', 'decomposition'],
            'stunt': ['stunted', 'stunting', 'dwarf']
        }
        
        for keyword, synonyms in symptom_keywords.items():
            if keyword in user_symptom:
                if any(syn in disease_symptom for syn in synonyms) or keyword in disease_symptom:
                    return True
            if any(syn in user_symptom for syn in synonyms):
                if keyword in disease_symptom or any(syn in disease_symptom for syn in synonyms):
                    return True
        
        return False
    
    def find_differentiating_symptoms(self, disease_names, excluded_symptoms=None):
        """
        JSON equivalent of:
        MATCH (d)-[r]-(s)
        WHERE d.name IN {disease_names} AND (s:Symptom OR 'Symptom' IN labels(s))
        RETURN DISTINCT s.name as symptom, collect(DISTINCT d.name) as diseases, count(DISTINCT d) as count
        ORDER BY count DESC, symptom LIMIT 20
        """
        if not disease_names:
            return []
        
        excluded_symptoms = excluded_symptoms or set()
        excluded_lower = {self._clean_symptom_text(s.lower()) for s in excluded_symptoms}
        
        symptom_disease_map = defaultdict(set)
        
        # Find diseases by name
        disease_nodes = []
        for node_type in ['Disease', 'PlantDisease']:
            for node in self.nodes_by_type.get(node_type, []):
                if node.get('name') in disease_names:
                    disease_nodes.append(node)
        
        # For each disease, find its symptoms
        for disease_node in disease_nodes:
            disease_id = disease_node['id']
            disease_name = disease_node.get('name', '')
            
            for rel in self.relationships_by_source.get(disease_id, []):
                if rel.get('type') in ['HAS_SYMPTOM', 'MANIFESTS_AS', 'SHOWS', 'EXHIBITS']:
                    target_node = self.nodes_by_id.get(rel['target'])
                    if target_node and target_node.get('type') == 'Symptom':
                        symptom_name = target_node.get('name', '')
                        if symptom_name:
                            clean_symptom = self._clean_symptom_text(symptom_name)
                            if clean_symptom and clean_symptom not in excluded_lower:
                                # Check if any excluded symptom matches this one
                                is_excluded = False
                                for excluded in excluded_lower:
                                    if self._symptoms_match(clean_symptom, excluded):
                                        is_excluded = True
                                        break
                                
                                if not is_excluded:
                                    symptom_disease_map[clean_symptom].add(disease_name)
        
        # Convert to result format
        results = []
        for symptom_name, diseases in symptom_disease_map.items():
            results.append({
                'symptom': symptom_name,
                'diseases': list(diseases),
                'count': len(diseases)
            })
        
        # Sort by count descending, then by symptom name
        results.sort(key=lambda x: (-x['count'], x['symptom']))
        return results[:20]
    
    def get_solutions_for_disease(self, disease_name):
        """
        JSON equivalent of:
        MATCH (d {name: '{disease_name}'})-[r]-(s)
        WHERE type(r) = 'HAS_SOLUTION' OR toLower(type(r)) CONTAINS 'solution' OR toLower(type(r)) CONTAINS 'treatment'
        RETURN s.name as solution_name, s.description as solution_description, 
               coalesce(s.treatment, s.content, s) as treatment
        """
        solutions = []
        
        # Find disease node by name (exact match)
        disease_node = None
        for node_type in ['Disease', 'PlantDisease']:
            for node in self.nodes_by_type.get(node_type, []):
                if node.get('name') == disease_name:
                    disease_node = node
                    break
            if disease_node:
                break
        
        if not disease_node:
            return solutions
        
        disease_id = disease_node['id']
        
        # Find solution relationships
        for rel in self.relationships_by_source.get(disease_id, []):
            rel_type = rel.get('type', '').lower()
            if ('solution' in rel_type or 'treatment' in rel_type or 
                rel_type == 'has_solution' or rel_type == 'treated_by'):
                
                target_node = self.nodes_by_id.get(rel['target'])
                if target_node and target_node.get('type') == 'Solution':
                    solution_name = target_node.get('name', '')
                    solution_description = target_node.get('description', '')
                    
                    # Try different fields for treatment information
                    treatment = (target_node.get('treatment') or 
                               target_node.get('content') or 
                               target_node.get('instructions') or
                               solution_name)  # Use solution name as fallback
                    
                    if solution_name:
                        solutions.append({
                            'solution_name': solution_name,
                            'solution_description': solution_description,
                            'treatment': treatment
                        })
        
        return solutions

# Adapter class to make JSONGraphConnector compatible with the existing diagnostic system
class JSONAuraDBAdapter:
    def __init__(self, json_file_path="plant_disease_graph.json"):
        self.json_connector = JSONGraphConnector(json_file_path)
    
    def close(self):
        self.json_connector.close()
    
    def execute_query(self, query, parameters=None):
        """
        This method tries to parse common Cypher patterns and convert them to JSON operations.
        For a full implementation, you'd need a proper Cypher parser.
        """
        
        # Handle disease-by-symptoms query
        if "collect(DISTINCT s.name) as all_disease_symptoms" in query:
            # Extract symptoms from query string (simplified)
            import re
            match = re.search(r'\{([^}]+)\}', query)
            if match:
                # This is a simplified extraction - in reality you'd parse parameters
                symptoms_str = match.group(1)
                # Remove quotes and split - this is very basic parsing
                symptoms = [s.strip().strip("'\"") for s in symptoms_str.split(',')]
                return self.json_connector.find_diseases_by_symptoms(symptoms)
        
        # Handle differentiating symptoms query  
        elif "collect(DISTINCT d.name) as diseases" in query and "count(DISTINCT d)" in query:
            # Extract disease names from query
            import re
            match = re.search(r'd\.name IN \{([^}]+)\}', query)
            if match:
                disease_names_str = match.group(1)
                disease_names = [s.strip().strip("'\"") for s in disease_names_str.split(',')]
                return self.json_connector.find_differentiating_symptoms(disease_names)
        
        # Handle solutions query
        elif "HAS_SOLUTION" in query or "solution" in query.lower():
            # Extract disease name from query
            import re
            match = re.search(r"name: '([^']+)'", query)
            if match:
                disease_name = match.group(1)
                return self.json_connector.get_solutions_for_disease(disease_name)
        
        # Handle schema queries
        elif "CALL db.labels()" in query:
            schema = self.json_connector.get_schema()
            return [{'labels': label} for label in schema['node_labels']]
        
        elif "CALL db.relationshipTypes()" in query:
            schema = self.json_connector.get_schema()
            return [{'relationshipTypes': rel_type} for rel_type in schema['relationship_types']]
        
        elif "labels(n) as label, count(n) as count" in query:
            schema = self.json_connector.get_schema()
            return [{'label': label, 'count': count} for label, count in schema['node_counts']]
        
        return []
    
    def get_schema(self):
        return self.json_connector.get_schema()

# Sample JSON structure for plant disease graph
def create_sample_json_graph():
    """Create a sample JSON graph structure"""
    sample_graph = {
        "nodes": [
            # Diseases
            {
                "id": "disease_1",
                "type": "Disease", 
                "name": "Bacterial Leaf Spot",
                "description": "A bacterial infection causing dark spots on leaves"
            },
            {
                "id": "disease_2", 
                "type": "Disease",
                "name": "Iron Chlorosis",
                "description": "Nutrient deficiency causing yellowing of leaves"
            },
            {
                "id": "disease_3",
                "type": "Disease", 
                "name": "Powdery Mildew",
                "description": "Fungal infection causing white powdery coating"
            },
            
            # Symptoms
            {
                "id": "symptom_1",
                "type": "Symptom",
                "name": "brown spots",
                "description": "Dark brown or black spots on leaf surface"
            },
            {
                "id": "symptom_2", 
                "type": "Symptom",
                "name": "yellowing leaves",
                "description": "Leaves turning yellow, often starting from edges"
            },
            {
                "id": "symptom_3",
                "type": "Symptom", 
                "name": "white powdery coating",
                "description": "White dusty substance on leaf surfaces"
            },
            {
                "id": "symptom_4",
                "type": "Symptom",
                "name": "leaf drop", 
                "description": "Premature falling of leaves"
            },
            {
                "id": "symptom_5",
                "type": "Symptom",
                "name": "stunted growth",
                "description": "Reduced plant growth and development"
            },
            
            # Solutions
            {
                "id": "solution_1",
                "type": "Solution",
                "name": "Copper Fungicide",
                "description": "Copper-based spray for bacterial control",
                "treatment": "Apply copper fungicide every 7-10 days until symptoms improve"
            },
            {
                "id": "solution_2",
                "type": "Solution", 
                "name": "Iron Supplement",
                "description": "Iron chelate fertilizer for chlorosis",
                "treatment": "Apply iron chelate to soil according to package directions"
            },
            {
                "id": "solution_3",
                "type": "Solution",
                "name": "Fungicide Spray",
                "description": "Anti-fungal treatment for mildew",
                "treatment": "Spray affected areas with fungicide weekly"
            }
        ],
        
        "relationships": [
            # Disease -> Symptom relationships
            {"source": "disease_1", "target": "symptom_1", "type": "HAS_SYMPTOM"},
            {"source": "disease_1", "target": "symptom_4", "type": "HAS_SYMPTOM"},
            {"source": "disease_2", "target": "symptom_2", "type": "HAS_SYMPTOM"},
            {"source": "disease_2", "target": "symptom_5", "type": "HAS_SYMPTOM"},
            {"source": "disease_3", "target": "symptom_3", "type": "HAS_SYMPTOM"},
            {"source": "disease_3", "target": "symptom_2", "type": "HAS_SYMPTOM"},
            
            # Disease -> Solution relationships  
            {"source": "disease_1", "target": "solution_1", "type": "HAS_SOLUTION"},
            {"source": "disease_2", "target": "solution_2", "type": "HAS_SOLUTION"},
            {"source": "disease_3", "target": "solution_3", "type": "HAS_SOLUTION"}
        ]
    }
    
    # Save sample to file
    with open("plant_disease_graph.json", "w", encoding="utf-8") as f:
        json.dump(sample_graph, f, indent=2, ensure_ascii=False)
    
    print("Sample plant disease graph saved to 'plant_disease_graph.json'")
    return sample_graph

if __name__ == "__main__":
    # Create sample JSON graph
    create_sample_json_graph()
    
    # Test the JSON connector
    connector = JSONAuraDBAdapter()
    
    print("\n=== Testing JSON Graph Connector ===")
    
    # Test schema
    schema = connector.get_schema()
    print(f"Schema: {schema}")
    
    # Test finding diseases by symptoms
    print(f"\nDiseases for 'yellowing leaves': {connector.json_connector.find_diseases_by_symptoms(['yellowing leaves'])}")
    
    # Test finding solutions
    print(f"\nSolutions for 'Iron Chlorosis': {connector.json_connector.get_solutions_for_disease('Iron Chlorosis')}")
    
    connector.close() 