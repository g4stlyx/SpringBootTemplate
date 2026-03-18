#!/usr/bin/env python3
"""
Template Name Update Script
Recursively updates template-related text throughout the codebase.
Handles different case variations and preserves code structure.

! python update_template_name.py oldName newName
"""

import os
import re
from pathlib import Path
from typing import List, Tuple, Dict
import sys


class TemplateReplacer:
    def __init__(self, root_path: str, old_name: str, new_name: str, dry_run: bool = True):
        self.root_path = Path(root_path)
        self.old_name = old_name
        self.new_name = new_name
        self.dry_run = dry_run
        self.changes: List[Dict] = []
        
        # Directories to skip
        self.skip_dirs = {
            '.git', '.github', '.vscode', 'node_modules', 'target', '.gradle',
            '__pycache__', '.pytest_cache', '.env', 'build', 'dist',
            '.idea', 'out', '.DS_Store'
        }
        
        # File extensions to process
        self.include_extensions = {
            # Java
            '.java', '.properties', '.xml', '.gradle', '.pom',
            # Documentation
            '.md', '.txt', '.rst',
            # Frontend
            '.ts', '.tsx', '.js', '.jsx', '.css', '.scss', '.html',
            # Configuration
            '.yml', '.yaml', '.json', '.env',
            # Scripts & other
            '.sh', '.bat', '.sql', '.dockerfile', 'Dockerfile',
            # Email templates
            '.ftl', '.html', '.vm',
            # Other common files
            '.config', '.conf'
        }

    def get_case_variations(self) -> Dict[str, str]:
        """Generate different case variations of the old name."""
        variations = {}
        
        # camelCase variations
        variations[self.old_name] = self.new_name  # exact match
        variations[self.old_name.lower()] = self.new_name.lower()
        variations[self.old_name.upper()] = self.new_name.upper()
        
        # PascalCase variations
        if len(self.old_name) > 0:
            pascal_old = self.old_name[0].upper() + self.old_name[1:] if len(self.old_name) > 1 else self.old_name.upper()
            pascal_new = self.new_name[0].upper() + self.new_name[1:] if len(self.new_name) > 1 else self.new_name.upper()
            variations[pascal_old] = pascal_new
        
        # CONSTANT_CASE variations
        constant_old = self.old_name.upper().replace(' ', '_')
        constant_new = self.new_name.upper().replace(' ', '_')
        variations[constant_old] = constant_new
        
        # snake_case variations
        snake_old = self.old_name.lower().replace(' ', '_')
        snake_new = self.new_name.lower().replace(' ', '_')
        variations[snake_old] = snake_new
        
        # kebab-case variations
        kebab_old = self.old_name.lower().replace(' ', '-')
        kebab_new = self.new_name.lower().replace(' ', '-')
        variations[kebab_old] = kebab_new
        
        return variations

    def should_process_file(self, file_path: Path) -> bool:
        """Determine if a file should be processed."""
        # Check if file extension is in the include list
        if file_path.suffix in self.include_extensions:
            return True
        
        # Check for specific files without extension
        if file_path.name in {'Dockerfile', '.env', '.env.example', 'Makefile'}:
            return True
        
        return False

    def should_skip_dir(self, dir_name: str) -> bool:
        """Check if a directory should be skipped."""
        return dir_name in self.skip_dirs

    def update_file_content(self, file_path: Path) -> Tuple[bool, int, str]:
        """Update content in a single file. Returns (changed, count, error)."""
        try:
            with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                original_content = f.read()
            
            new_content = original_content
            total_replacements = 0
            
            # Apply all case variations
            variations = self.get_case_variations()
            for old_variant, new_variant in variations.items():
                # Use regex with word boundaries to avoid partial matches
                pattern = r'\b' + re.escape(old_variant) + r'\b'
                matches = len(re.findall(pattern, new_content))
                new_content = re.sub(pattern, new_variant, new_content)
                total_replacements += matches
            
            if new_content != original_content:
                if not self.dry_run:
                    with open(file_path, 'w', encoding='utf-8') as f:
                        f.write(new_content)
                return True, total_replacements, ""
            
            return False, 0, ""
        
        except Exception as e:
            return False, 0, str(e)

    def update_file_name(self, file_path: Path) -> Tuple[bool, str]:
        """Update file names if they contain template references."""
        try:
            new_name = file_path.name
            variations = self.get_case_variations()
            
            for old_variant, new_variant in variations.items():
                if old_variant in new_name:
                    new_name = new_name.replace(old_variant, new_variant)
            
            if new_name != file_path.name:
                new_path = file_path.parent / new_name
                if not self.dry_run:
                    file_path.rename(new_path)
                return True, new_path.name
            
            return False, file_path.name
        
        except Exception as e:
            return False, str(e)

    def update_directory_names(self) -> List[Dict]:
        """Update directory names containing template references."""
        dir_renames = []
        variations = self.get_case_variations()
        
        for root, dirs, files in os.walk(self.root_path, topdown=False):
            for dir_name in dirs:
                if self.should_skip_dir(dir_name):
                    continue
                
                new_dir_name = dir_name
                for old_variant, new_variant in variations.items():
                    if old_variant in new_dir_name:
                        new_dir_name = new_dir_name.replace(old_variant, new_variant)
                
                if new_dir_name != dir_name:
                    old_path = Path(root) / dir_name
                    new_path = Path(root) / new_dir_name
                    
                    try:
                        if not self.dry_run:
                            old_path.rename(new_path)
                        dir_renames.append({
                            'type': 'directory',
                            'old': str(old_path),
                            'new': str(new_path),
                            'status': 'renamed'
                        })
                    except Exception as e:
                        dir_renames.append({
                            'type': 'directory',
                            'old': str(old_path),
                            'new': str(new_path),
                            'status': f'error: {e}'
                        })
        
        return dir_renames

    def run(self) -> None:
        """Execute the template name replacement."""
        print(f"\n{'='*60}")
        print(f"Template Name Replacement Script")
        print(f"{'='*60}")
        print(f"Root path: {self.root_path}")
        print(f"Old name: {self.old_name}")
        print(f"New name: {self.new_name}")
        print(f"Mode: {'DRY RUN' if self.dry_run else 'EXECUTE'}")
        print(f"{'='*60}\n")
        
        # Generate case variations
        variations = self.get_case_variations()
        print("Case variations to replace:")
        for old_var, new_var in variations.items():
            print(f"  {old_var:30} → {new_var}")
        print()
        
        file_changes = []
        total_replacements = 0
        
        # Process files
        print("Processing files...")
        for root, dirs, files in os.walk(self.root_path):
            # Filter directories to skip
            dirs[:] = [d for d in dirs if not self.should_skip_dir(d)]
            
            for file_name in files:
                file_path = Path(root) / file_name
                
                if not self.should_process_file(file_path):
                    continue
                
                # Update file content
                changed, replacements, error = self.update_file_content(file_path)
                if changed:
                    file_changes.append({
                        'type': 'content',
                        'path': str(file_path.relative_to(self.root_path)),
                        'replacements': replacements,
                        'status': 'updated'
                    })
                    total_replacements += replacements
                
                if error:
                    file_changes.append({
                        'type': 'content',
                        'path': str(file_path.relative_to(self.root_path)),
                        'status': f'error: {error}'
                    })
                
                # Update file name
                renamed, new_name = self.update_file_name(file_path)
                if renamed:
                    file_changes.append({
                        'type': 'filename',
                        'path': str(file_path.relative_to(self.root_path)),
                        'new_name': new_name,
                        'status': 'renamed'
                    })
        
        # Update directory names
        print("Processing directories...")
        dir_renames = self.update_directory_names()
        
        # Print summary
        print(f"\n{'='*60}")
        print(f"Summary")
        print(f"{'='*60}")
        print(f"Files modified: {len([c for c in file_changes if c['type'] == 'content' and c['status'] == 'updated'])}")
        print(f"Total replacements: {total_replacements}")
        print(f"Files renamed: {len([c for c in file_changes if c['type'] == 'filename'])}")
        print(f"Directories renamed: {len(dir_renames)}")
        print()
        
        if file_changes or dir_renames:
            print("Changes:")
            for change in file_changes:
                if change['status'] == 'updated':
                    print(f"  ✓ {change['path']} ({change['replacements']} replacements)")
                elif change['status'] == 'renamed':
                    print(f"  ✓ RENAMED: {change['path']} → {change['new_name']}")
                else:
                    print(f"  ✗ {change['path']}: {change['status']}")
            
            for change in dir_renames:
                if change['status'] == 'renamed':
                    print(f"  ✓ DIR RENAMED: {change['old']} → {change['new']}")
                else:
                    print(f"  ✗ {change['old']}: {change['status']}")
        else:
            print("No changes to make.")
        
        print(f"\n{'='*60}\n")


def get_user_input() -> Tuple[str, str, bool]:
    """Get user input for old and new names."""
    print("\nTemplate Name Replacement Tool")
    print("-" * 40)
    
    old_name = input("Enter old name to replace (default: 'templateApp'): ").strip()
    if not old_name:
        old_name = "templateApp"
    
    new_name = input("Enter new name: ").strip()
    if not new_name:
        print("Error: New name cannot be empty!")
        sys.exit(1)
    
    # Ask for dry run
    dry_run_input = input("Perform dry run first? (y/n, default: y): ").strip().lower()
    dry_run = dry_run_input != 'n'
    
    return old_name, new_name, dry_run


def main():
    if len(sys.argv) > 2:
        old_name = sys.argv[1]
        new_name = sys.argv[2]
        dry_run = len(sys.argv) <= 3 or sys.argv[3].lower() != '--execute'
    else:
        old_name, new_name, dry_run = get_user_input()
    
    root_path = Path(__file__).parent
    
    # Run dry run first
    if dry_run:
        replacer = TemplateReplacer(root_path, old_name, new_name, dry_run=True)
        replacer.run()
        
        # Ask if user wants to proceed
        proceed = input("Do you want to proceed with the replacement? (y/n): ").strip().lower()
        if proceed != 'y':
            print("Cancelled.")
            sys.exit(0)
    
    # Run actual replacement
    replacer = TemplateReplacer(root_path, old_name, new_name, dry_run=False)
    replacer.run()
    print("Replacement complete!")


if __name__ == '__main__':
    main()
