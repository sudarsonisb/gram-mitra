import os
import json
import requests
from json_graph_connector import JSONAuraDBAdapter
from dotenv import load_dotenv

load_dotenv()

# Configuration for LLM (only for final summaries)
OLLAMA_BASE_URL = os.getenv('OLLAMA_BASE_URL', 'http://localhost:11434')
OLLAMA_MODEL = os.getenv('OLLAMA_MODEL', 'gemma3n:e4b')

class OllamaGemmaConnector:
    def __init__(self):
        self.base_url = OLLAMA_BASE_URL
        self.model = OLLAMA_MODEL
        self.api_url = f"{self.base_url}/api/generate"
    
    def generate_summary(self, symptoms, context=""):
        """Generate plant disease diagnosis summary - ONLY USE FOR FINAL DIAGNOSIS"""
        system_prompt = """Provide plant disease diagnosis with: 
1) Symptom analysis 
2) Disease identification 
3) Treatment recommendations 
4) Prevention measures"""
        
        payload = {
            "model": self.model,
            "prompt": f"{system_prompt}\n\nCONTEXT: {context}\nSYMPTOMS: {symptoms}",
            "stream": False,
            "options": {"temperature": 0.6, "num_predict": 500}
        }
        
        try:
            response = requests.post(self.api_url, json=payload, timeout=60)
            response.raise_for_status()
            return response.json()["response"].strip()
        except Exception as e:
            return f"Error generating summary: {e}"
    
    def check_connection(self):
        try:
            response = requests.get(f"{self.base_url}/api/tags", timeout=5)
            models = [m["name"] for m in response.json()["models"]]
            if self.model in models:
                return True, f"✅ {self.model} available"
            return False, f"❌ {self.model} not found. Available: {models}"
        except Exception as e:
            return False, f"❌ Connection failed: {e}"

class PlantDiseaseDignosticSystemGraphDirect:
    def __init__(self, json_connector, llm_connector):
        self.db = json_connector
        self.llm = llm_connector
        self.conversation_history = []
        self.confirmed_symptoms = set()
        self.ruled_out_symptoms = set()
        self.asked_symptoms = set()
        self.possible_diseases = []
        self.last_asked_symptom = None
    
    def initialize(self):
        self.schema = self.db.get_schema()
        print("🌱 Plant Disease Diagnostic System (Graph Direct) initialized")
        print("🎯 Using DIRECT graph traversal for symptom selection!")
        return self.schema
    
    def find_diseases_by_symptoms(self, symptoms):
        """Find diseases using JSON connector"""
        if not symptoms:
            return []
        
        results = self.db.json_connector.find_diseases_by_symptoms(list(symptoms))
        
        diseases = []
        for r in results:
            diseases.append({
                'name': r['disease'],
                'description': r.get('description', ''),
                'related_symptoms': r.get('matched', []),
                'all_symptoms': r.get('all_symptoms', []),
                'match_count': r.get('match_count', 0),
                'total_symptoms': r.get('total_symptoms', 1),
                'match_percentage': r.get('match_percentage', 0.0)
            })
        
        return diseases[:8]
    
    def find_best_differentiating_symptom_from_graph(self):
        """DIRECTLY use the graph to find the best symptom to ask about next"""
        if not self.possible_diseases:
            print("❌ No possible diseases to differentiate between")
            return None
        
        print(f"🔍 Finding differentiating symptoms for {len(self.possible_diseases)} diseases:")
        for disease in self.possible_diseases[:3]:
            print(f"   - {disease['name']} ({disease.get('match_percentage', 0):.1%})")
        
        disease_names = [d['name'] for d in self.possible_diseases[:5]]
        
        # Get excluded symptoms
        all_excluded = set()
        for symptom in self.confirmed_symptoms:
            all_excluded.add(symptom.lower().strip())
        for symptom in self.ruled_out_symptoms:
            all_excluded.add(symptom.lower().strip())
        for symptom in self.asked_symptoms:
            all_excluded.add(symptom.lower().strip())
        
        print(f"🚫 Excluding {len(all_excluded)} symptoms: {list(all_excluded)}")
        
        # Get differentiating symptoms from graph
        results = self.db.json_connector.find_differentiating_symptoms(disease_names, all_excluded)
        
        print(f"📋 Graph returned {len(results)} differentiating symptoms:")
        for i, r in enumerate(results[:5]):
            print(f"   {i+1}. '{r['symptom']}' (helps with {len(r['diseases'])} diseases: {r['diseases'][:2]})")
        
        if not results:
            print("❌ No differentiating symptoms found in graph")
            return None
        
        # Score symptoms by how well they differentiate between diseases
        best_symptom = None
        best_score = 0
        
        for symptom_info in results:
            symptom_name = symptom_info['symptom']
            diseases_with_symptom = set(symptom_info['diseases'])
            
            # Skip if already processed
            if symptom_name.lower().strip() in all_excluded:
                continue
            
            # Calculate differentiation score:
            # - How many diseases have this symptom
            # - How many don't have it (helps rule out)
            # - Prefer symptoms that split diseases roughly evenly
            
            num_diseases_with = len(diseases_with_symptom)
            num_diseases_without = len(disease_names) - num_diseases_with
            
            # Score based on how evenly it splits the diseases
            if num_diseases_with == 0 or num_diseases_without == 0:
                score = 0  # Doesn't help differentiate
            else:
                # Higher score for more balanced splits
                balance_score = min(num_diseases_with, num_diseases_without)
                # Bonus for symptoms that appear in multiple diseases (more informative)
                frequency_bonus = num_diseases_with * 0.1
                score = balance_score + frequency_bonus
            
            print(f"   📊 '{symptom_name}': {num_diseases_with} have it, {num_diseases_without} don't → Score: {score:.1f}")
            
            if score > best_score:
                best_score = score
                best_symptom = symptom_info
        
        if best_symptom:
            print(f"🏆 Best symptom to ask: '{best_symptom['symptom']}' (score: {best_score:.1f})")
            print(f"   This symptom helps differentiate: {best_symptom['diseases']}")
            return best_symptom['symptom']
        else:
            print("❌ No good differentiating symptom found")
            return None
    
    def get_solution_for_disease(self, disease_name):
        """Get solutions using JSON connector"""
        return self.db.json_connector.get_solutions_for_disease(disease_name)
    
    def should_diagnose(self):
        """Check if we should provide diagnosis"""
        if not self.confirmed_symptoms or len(self.confirmed_symptoms) < 2:
            return None
        
        diseases = self.find_diseases_by_symptoms(self.confirmed_symptoms)
        if not diseases:
            return None
        
        top = diseases[0]
        second = diseases[1] if len(diseases) > 1 else None
        
        print(f"🏥 Diagnosis check - Top disease: {top['name']} ({top.get('match_percentage', 0):.1%})")
        if second:
            print(f"   Second: {second['name']} ({second.get('match_percentage', 0):.1%})")
        
        # Single disease
        if len(diseases) == 1:
            print("✅ Only one disease matches - definitive diagnosis")
            return top
        
        if second:
            top_pct = top.get('match_percentage', 0.0)
            second_pct = second.get('match_percentage', 0.0)
            gap = top_pct - second_pct
            
            print(f"   Gap between top and second: {gap:.1%}")
            
            # Significant advantage
            if gap > 0.20 or (top_pct > 0.70 and gap > 0.10):
                print("✅ Top disease has significant advantage - diagnosing")
                return top
        
        # High confidence with multiple symptoms
        if top.get('match_count', 0) >= 3 and top.get('match_percentage', 0.0) >= 0.60:
            print("✅ High confidence with multiple matching symptoms - diagnosing")
            return top
        
        print("⏳ Not enough confidence yet - continue asking")
        return None
    
    def create_diagnosis_context(self, disease, additional_info=""):
        """Create context including solutions"""
        solutions = self.get_solution_for_disease(disease['name'])
        
        context_parts = [
            f"CONFIRMED symptoms: {list(self.confirmed_symptoms)}",
            f"RULED OUT symptoms: {list(self.ruled_out_symptoms)}",
            f"DIAGNOSED disease: {disease['name']} ({disease.get('match_percentage', 0.0):.1%} match)",
            f"MATCHED symptoms: {disease.get('related_symptoms', [])}"
        ]
        
        if additional_info:
            context_parts.append(additional_info)
        
        if solutions:
            solution_names = [s['solution_name'] for s in solutions if s['solution_name']]
            context_parts.append(f"AVAILABLE SOLUTIONS: {solution_names}")
            for sol in solutions:
                if sol.get('solution_description'):
                    context_parts.append(f"SOLUTION - {sol['solution_name']}: {sol['solution_description']}")
                if sol.get('treatment'):
                    context_parts.append(f"TREATMENT - {sol['solution_name']}: {sol['treatment']}")
        else:
            context_parts.append("NO SPECIFIC SOLUTIONS FOUND IN GRAPH")
        
        return " | ".join(context_parts)
    
    def parse_response(self, user_input):
        """Parse user response"""
        lower = user_input.lower().strip()
        if any(word in lower for word in ['yes', 'y', 'yeah', 'definitely', 'observed']):
            return 'confirmed'
        elif any(word in lower for word in ['no', 'n', 'nope', 'not', 'never', 'absent']):
            return 'denied'
        return 'new_symptom'
    
    def format_question(self, symptom):
        """Format a natural question for the given symptom"""
        if not symptom:
            return "Please describe any other symptoms you observe."
            
        # Clean the symptom
        symptom = symptom.strip().lower()
        
        # Choose appropriate question format based on symptom type
        if any(word in symptom for word in ['soil', 'ground', 'environment']):
            return f"Do you observe {symptom} around the plant?"
        elif any(word in symptom for word in ['stress', 'condition', 'temperature']):
            return f"Is the plant showing signs of {symptom}?"
        elif symptom.startswith('dry') or symptom.startswith('wet'):
            return f"Are there {symptom} conditions affecting the plant?"
        else:
            return f"Do you observe {symptom} on the plant?"
    
    def process_user_input(self, user_input):
        """Main processing logic - DIRECT GRAPH USAGE!"""
        user_input = user_input.strip()
        self.conversation_history.append({"user": user_input, "timestamp": len(self.conversation_history)})
        
        if len(self.conversation_history) == 1:  # First interaction
            print(f"🌱 Initial symptom: '{user_input}'")
            self.confirmed_symptoms.add(user_input.lower())
            self.possible_diseases = self.find_diseases_by_symptoms(self.confirmed_symptoms)
            
            print(f"🔍 Found {len(self.possible_diseases)} possible diseases from graph:")
            for disease in self.possible_diseases[:3]:
                print(f"   - {disease['name']} ({disease.get('match_percentage', 0):.1%} match)")
            
            # Check for immediate diagnosis
            definitive = self.should_diagnose()
            if definitive:
                context = self.create_diagnosis_context(definitive, "IMMEDIATE DIAGNOSIS")
                response = self.llm.generate_summary(list(self.confirmed_symptoms), context)
                self.conversation_history.append({"assistant": response, "timestamp": len(self.conversation_history)})
                self.show_status()
                return f"🏆 IMMEDIATE DIAGNOSIS!\n\n{response}"
        
        else:  # Follow-up interactions
            response_type = self.parse_response(user_input)
            print(f"📝 User response: '{user_input}' → {response_type}")
            
            if response_type == 'confirmed':
                if self.last_asked_symptom:
                    self.confirmed_symptoms.add(self.last_asked_symptom.lower())
                    print(f"✅ Confirmed: '{self.last_asked_symptom}'")
                else:
                    print("⚠️ User said yes but no symptom was being tracked!")
                    
            elif response_type == 'denied':
                if self.last_asked_symptom:
                    self.ruled_out_symptoms.add(self.last_asked_symptom.lower())
                    print(f"❌ Ruled out: '{self.last_asked_symptom}'")
                else:
                    print("⚠️ User said no but no symptom was being tracked!")
                    
            elif response_type == 'new_symptom':
                self.confirmed_symptoms.add(user_input.lower())
                print(f"➕ New symptom: '{user_input}'")
            
            # Update diseases based on new information
            print(f"\n🔄 Updating disease list...")
            self.possible_diseases = self.find_diseases_by_symptoms(self.confirmed_symptoms)
            
            print(f"🎯 Updated diseases ({len(self.possible_diseases)}):")
            for disease in self.possible_diseases[:3]:
                print(f"   - {disease['name']} ({disease.get('match_percentage', 0):.1%} match)")
            
            # Check for diagnosis
            definitive = self.should_diagnose()
            if definitive:
                context = self.create_diagnosis_context(definitive, 
                    f"DIAGNOSIS after {len(self.confirmed_symptoms)} symptoms confirmed")
                response = self.llm.generate_summary(list(self.confirmed_symptoms), context)
                self.conversation_history.append({"assistant": response, "timestamp": len(self.conversation_history)})
                self.show_status()
                return f"🏆 DIAGNOSIS COMPLETE!\n\n{response}"
            
            # Check if we have enough symptoms but need to force diagnosis
            if len(self.confirmed_symptoms) >= 4 and self.possible_diseases:
                print("⏰ Enough symptoms collected - forcing diagnosis")
                top_disease = self.possible_diseases[0]
                context = self.create_diagnosis_context(top_disease, "ANALYSIS COMPLETE - Sufficient symptoms")
                response = self.llm.generate_summary(list(self.confirmed_symptoms), context)
                self.conversation_history.append({"assistant": response, "timestamp": len(self.conversation_history)})
                self.show_status()
                return f"🏆 DIAGNOSIS (Analysis Complete)!\n\n{response}"
        
        # Get next symptom to ask about - DIRECT FROM GRAPH!
        print(f"\n🔍 Finding next symptom from graph...")
        next_symptom = self.find_best_differentiating_symptom_from_graph()
        
        if next_symptom:
            # Store the symptom we're asking about
            self.last_asked_symptom = next_symptom
            self.asked_symptoms.add(next_symptom.lower())
            
            # Format the question
            question = self.format_question(next_symptom)
            
            print(f"❓ Asking: '{question}'")
            
            self.conversation_history.append({"assistant": question, "timestamp": len(self.conversation_history)})
            self.show_status()
            return question
        else:
            # No more symptoms to ask - provide final diagnosis
            print("🏁 No more differentiating symptoms available")
            if self.possible_diseases:
                top_disease = self.possible_diseases[0]
                context = self.create_diagnosis_context(top_disease, "NO MORE QUESTIONS - Final diagnosis")
                response = self.llm.generate_summary(list(self.confirmed_symptoms), context)
                self.conversation_history.append({"assistant": response, "timestamp": len(self.conversation_history)})
                self.show_status()
                return f"🏆 FINAL DIAGNOSIS!\n\n{response}"
            else:
                return "I need more information. Please describe any other symptoms you observe."
    
    def show_status(self):
        """Show current symptom status"""
        print(f"\n📊 CURRENT STATUS:")
        print(f"   ✅ Confirmed: {list(self.confirmed_symptoms) if self.confirmed_symptoms else 'None'}")
        print(f"   ❌ Ruled out: {list(self.ruled_out_symptoms) if self.ruled_out_symptoms else 'None'}")
        print(f"   🎯 Diseases: {len(self.possible_diseases)} possible")
        print("=" * 50)
    
    def get_diagnostic_summary(self):
        """Generate diagnostic summary"""
        if not self.confirmed_symptoms:
            return "No symptoms confirmed yet. Please describe what you observe on the plant."
        
        diseases = self.find_diseases_by_symptoms(self.confirmed_symptoms)
        if diseases:
            top_disease = diseases[0]
            context = self.create_diagnosis_context(top_disease, "SUMMARY ANALYSIS")
            return self.llm.generate_summary(list(self.confirmed_symptoms), context)
        else:
            return "No matching diseases found in graph for the confirmed symptoms."
    
    def reset_conversation(self):
        """Reset conversation"""
        self.conversation_history = []
        self.confirmed_symptoms = set()
        self.ruled_out_symptoms = set()
        self.asked_symptoms = set()
        self.possible_diseases = []
        self.last_asked_symptom = None
        print("Conversation reset.")

def main_graph_direct():
    """Main function using DIRECT graph traversal - no LLM for symptom selection!"""
    print("🌱 === PLANT DISEASE DIAGNOSTIC SYSTEM (GRAPH DIRECT) ===")
    print("🎯 DIRECT graph traversal - no LLM for symptom selection!")
    print("🔍 Knowledge graph drives the conversation flow!")
    
    # Initialize
    db = JSONAuraDBAdapter("plant_disease_graph.json")
    llm = OllamaGemmaConnector()
    
    # Check connection
    is_connected, message = llm.check_connection()
    print(f"Ollama Status: {message}")
    if not is_connected:
        print("Please ensure Ollama is running with gemma3n:e4b model")
        return
    
    # Initialize system
    diagnostic_system = PlantDiseaseDignosticSystemGraphDirect(db, llm)
    schema = diagnostic_system.initialize()
    
    if schema['node_labels']:
        print(f"Graph loaded: {len(schema['node_labels'])} node types, {len(schema['relationship_types'])} relationships")
        
        # Show some graph statistics
        for node_type, count in schema['node_counts'][:3]:
            print(f"   - {node_type}: {count} nodes")
    
    print("\n" + "="*60)
    print("🌿 PLANT DISEASE DIAGNOSTIC ASSISTANT (GRAPH DIRECT)")
    print("💡 Graph data directly drives symptom selection!")
    print("Commands: 'summary', 'state', 'reset', 'quit'")
    print("="*60)
    
    # Main loop
    while True:
        try:
            user_input = input("\n🌱 You: ").strip()
            if not user_input:
                continue
            
            if user_input.lower() in ['quit', 'exit']:
                print("👋 Goodbye!")
                break
            elif user_input.lower() == 'summary':
                print("\n" + "="*40)
                print(diagnostic_system.get_diagnostic_summary())
                print("="*40)
            elif user_input.lower() == 'state':
                print("\n📊 CURRENT DIAGNOSTIC STATE:")
                print(f"Confirmed: {list(diagnostic_system.confirmed_symptoms)}")
                print(f"Ruled out: {list(diagnostic_system.ruled_out_symptoms)}")
                print(f"Asked: {list(diagnostic_system.asked_symptoms)}")
                print(f"Diseases: {len(diagnostic_system.possible_diseases)}")
                for disease in diagnostic_system.possible_diseases[:3]:
                    print(f"  - {disease['name']} ({disease.get('match_percentage', 0):.1%})")
            elif user_input.lower() == 'reset':
                diagnostic_system.reset_conversation()
            else:
                response = diagnostic_system.process_user_input(user_input)
                print(f"\nPlant Pathologist: {response}")
                
        except KeyboardInterrupt:
            print("\n👋 Session interrupted!")
            break
        except Exception as e:
            print(f"Error: {e}")
    
    db.close()

if __name__ == "__main__":
    main_graph_direct() 